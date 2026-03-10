# Data Flow

> **Design context**: For the canonical sequence diagrams, domain model, and consistency requirements that this flow implements, see [design/04-sequence-diagrams.md](https://github.com/stevelefi/autotrading/blob/main/docs/design/04-sequence-diagrams.md) and [ORDER\_CONSISTENCY\_AND\_RECONCILIATION.md](https://github.com/stevelefi/autotrading/blob/main/docs/ORDER_CONSISTENCY_AND_RECONCILIATION.md) in the spec repo.

## 1. Happy-Path Signal-to-Fill Flow

```
[Trader UI / External System]
    │
    │  HTTP POST /api/v1/trade-events/manual
    │  Headers: X-Actor-Id, X-Request-Id
    │  Body: { client_event_id, agent_id, instrument_id, side, qty, ... }
    ▼
┌──────────────────────────────────┐
│    ingress-gateway-service       │  HTTP :18080
│                                  │
│  1. Normalize + validate         │
│  2. @Transactional:              │
│     - Persist → ingress_raw_events  (always; audit trail)
│     - Claim client_event_id      │
│  3. afterCommit() callback:      │
│     KafkaFirstPublisher.publish()│
│     ├─ Kafka OK  → delivered     │
│     └─ Kafka fail → outbox_events│  REQUIRES_NEW TX; polled with backoff
└──────────────┬───────────────────┘
               │  ingress.events.normalized.v1
               ▼
┌──────────────────────────────────┐
│   event-processor-service        │  HTTP :18085
│                                  │
│  1. @Transactional + ConsumerDeduper (consumer_inbox)
│  2. EventProcessorRouter.route() │
│  3. Persist → routed_trade_events│
│  4. DirectKafkaPublisher.publish() ┼──► trade.events.routed.v1
│     fail → throw → rollback → re-queue
└──────────────┬───────────────────┘
               │  trade.events.routed.v1
               ▼
┌──────────────────────────────────┐
│   agent-runtime-service          │  HTTP :18086
│                                  │
│  1. ConsumerDeduper              │
│  2. Persist → signals  ← FIRST   │  crash-safe: persist before gRPC
│  3. gRPC EvaluateSignal ─────────┼──► risk-service :19091
└──────────────────────────────────┘
               │  gRPC EvaluateSignalRequest
               │    agent_id, signal_id, instrument_id
               │    trade_event_id, raw_event_id  (lineage)
               │    origin_source_type, origin_source_event_id
               │    side, qty, order_type, time_in_force
               ▼
┌──────────────────────────────────┐
│   risk-service                   │  HTTP :18081 / gRPC :19091
│                                  │
│  1. validateLineage()            │  → INVALID_ARGUMENT if missing
│  2. SimplePolicyEngine.evaluate()│
│  3. @Transactional:              │
│     - Persist → risk_decisions   │
│     - Persist → policy_decision_log
│  4. DirectKafkaPublisher           │
│     .publishBestEffort() ────────┼──► policy.evaluations.audit.v1
│     (best-effort; Kafka fail logged as WARN, gRPC response unaffected)
│  5. gRPC CreateOrderIntent ──────┼──► order-service :19092
└──────────────────────────────────┘
               │  gRPC CreateOrderIntentRequest
               │    decision (APPROVE / DENY)
               │    policy_version, matched_rule_ids
               ▼
┌──────────────────────────────────┐
│   order-service                  │  HTTP :18082 / gRPC :19092
│                                  │
│  1. Check tradingMode != FROZEN  │
│  2. IdempotencyService.claim()   │
│  3. gRPC SubmitOrder ────────────┼──► ibkr-connector-service :19093
│  4. Persist → order_intents      │
│     Persist → order_ledger       │
│     Persist → order_state_history│
│  5. TimeoutWatchdog (60s poll)   │
└──────────────────────────────────┘
               │  gRPC SubmitOrderRequest
               │    order_intent_id, agent_id, instrument_id
               │    side, qty, order_type, time_in_force
               │    submission_deadline_ms: 60000
               ▼
┌──────────────────────────────────┐
│   ibkr-connector-service         │  HTTP :18083 / gRPC :19093
│                                  │
│  1. IdempotencyService.claim()   │
│  2. @Transactional:              │
│     - Persist → broker_orders    │
│     - DirectKafkaPublisher       │
│       .publishBestEffort() ──────┼──► orders.status.v1
│  3. Return broker_submit_id      │
│                                  │
│  [Later — fill callback]         │
│  4. @Transactional:              │
│     - Persist → executions       │
│     - DirectKafkaPublisher       │
│       .publishBestEffort() ──────┼──► fills.executed.v1
└──────────────┬───────────────────┘
               │  fills.executed.v1
               ▼
┌──────────────────────────────────┐
│   performance-service            │  HTTP :18087
│                                  │
│  1. ConsumerDeduper              │
│  2. ConcurrentHashMap.compute()  │  atomic position update
│  3. Persist → positions          │
│  4. Persist → pnl_snapshots      │
└──────────────────────────────────┘
```

---

## 2. Kafka-First Publishing with Outbox Fallback (ingress only)

`ingress-gateway-service` is the **only** service that still uses the transactional outbox, and
only as a Kafka-failure safety net.  All other services publish to Kafka directly in the request
path and rely on broker-level retry (uncommitted offset) for resilience.

### ingress-gateway-service — Kafka-first flow

```
IngressService.accept()  (@Transactional)
    │
    ├─ rawEventRepository.save()      ─┐  always — audit / troubleshoot trail
    └─ idempotencyService.markCompleted() ┘  committed atomically
               │
    TransactionSynchronization.afterCommit()   ← fires after TX commits
               │
    KafkaFirstPublisher.publish(topic, partitionKey, payload)
               │
               ├─ DirectKafkaPublisher.publish()   ← doubling backoff (§ below)
               │       OK  → delivered to ingress.events.normalized.v1
               │
               └─ Kafka exhausts budget (KafkaPublishException)
                       │
                       └─ outboxRepository.append()  [PROPAGATION_REQUIRES_NEW TX]
                                  │
                                  │  outbox_events table (status=NEW)
                                  │
                              OutboxPollerLifecycle  (ingress only; 500 ms)
                                  │
                              OutboxDispatcher.dispatch()
                                  │  status IN ('NEW','FAILED')
                                  │    AND (next_retry_at IS NULL
                                  │         OR next_retry_at <= NOW())
                                  │
                                  ├─ Kafka OK  → status='DELIVERED'
                                  └─ Kafka fail → status='FAILED'
                                       next_retry_at = now() + min(2^attempt, 300)s
                                       attempts >= 10 → next_retry_at=NULL (parked)
```

### DirectKafkaPublisher retry budget

Every `DirectKafkaPublisher.publish()` call retries with **doubling back-off** until the
total budget (`autotrading.kafka.publish-timeout-ms`, default **500 ms** per attempt;
`TOTAL_PUBLISH_BUDGET_MS` hard-cap **5 000 ms** globally) is exhausted:

```
attempt 1: send → wait up to publish-timeout-ms
attempt 2: sleep 10 ms  → retry
attempt 3: sleep 20 ms  → retry
attempt 4: sleep 40 ms  → retry
...                        (doubling each time)
Budget > 5 000 ms total  → throw KafkaPublishException
```

For **audit / status paths** (`publishBestEffort`) the `KafkaPublishException` is caught
internally and logged at WARN — the caller never sees an exception.

### All other services — direct Kafka publish

```
@Transactional  @KafkaListener method
    │
    ├─ ConsumerDeduper.runOnce()  (insert consumer_inbox; skip if duplicate)
    ├─ domain logic + repository.save()
    └─ DirectKafkaPublisher.publish(topic, key, payload)
           │  (internal doubling-backoff up to 5 s total)
           ├─ OK  → method returns, offset committed
           └─ KafkaPublishException propagates
                  → @Transactional rolls back DB writes
                  → offset NOT committed
                  → Kafka re-delivers (broker retry)
```

`autotrading.outbox.poller.enabled` is `false` for every service except ingress — the
`OutboxPollerLifecycle` and `OutboxDispatcher` beans are not created in those processes.

### Consumer-side deduplication (all services)

```
Kafka record
    ▼
@KafkaListener + @Transactional
    │
    └─ ConsumerDeduper.runOnce(consumerName, eventId, work)
            │
            ├─ INSERT INTO consumer_inbox (consumer_name, event_id)
            │    conflict → skip (already processed, no-op)
            │
            └─ work.run()  ← domain logic in same TX
```

Guarantees:
- Raw ingress event is always persisted for audit regardless of Kafka state
- A consumer crash before TX commit replays the record (no data loss)
- A replayed record hits `ConsumerDeduper` — exactly-once processing
- Downstream Kafka failures surface immediately (throw → re-queue) rather than silently queuing in an outbox

---

## 3. gRPC Command Chain

```
agent-runtime-service
    │  EvaluateSignalRequest
    ▼
risk-service  →  validateLineage()  →  SimplePolicyEngine.evaluate()
    │  CreateOrderIntentRequest (decision=APPROVE/DENY, policy metadata)
    ▼
order-service  →  trading mode check  →  idempotency claim
    │  SubmitOrderRequest (order_intent_id, side, qty, deadline)
    ▼
ibkr-connector-service  →  idempotency claim  →  persist  →  broker_submit_id
    │
    └─► SubmitOrderResponse back up the call chain
        order-service persists order_intents, order_ledger, order_state_history
        risk-service persists risk_decisions, policy_decision_log;
        best-effort publish → policy.evaluations.audit.v1
        agent-runtime-service receives EvaluateSignalResponse
```

Error handling: every method wraps in `try-catch`; `IllegalArgumentException` → `INVALID_ARGUMENT`, anything else → `INTERNAL`. `MDC.clear()` fires in `finally` on every gRPC thread.

---

## 4. Order Lifecycle States

```
INTENT_CREATED
      │
      ▼
SUBMIT_REQUESTED ──(broker ack within 60s)──► SUBMITTED_ACKED
      │                                              │
      │  (no ack within 60s)                        │  (fill received)
      ▼                                              ▼
UNKNOWN_PENDING_RECON                      FILLED / PARTIAL_FILL
      │
      ▼
tradingMode = FROZEN  (system-wide)
system.alerts.v1: CRITICAL:status_timeout_60s
```

`OrderTimeoutWatchdogLifecycle` polls every 5 seconds (configurable via `order.timeout.watchdog.interval.ms`). On timeout: transitions the order, freezes trading mode, appends an alert to the outbox.

---

## 5. client_event_id Deduplication Flow

```
Client sends client_event_id = "k-abc-123"

ingress-gateway-service:
    claim({key="k-abc-123", payloadHash=sha(body)})
    → NEW     → proceed, persist, publish
    → REPLAY  → return cached response (same event_id, first-write-wins)
    → CONFLICT → not raised (first-write-wins semantics; same key, any payload = REPLAY)

order-service:
    same claim on CreateOrderIntent
    → REPLAY → return cached order_intent_id without re-submitting to broker

ibkr-connector-service:
    same claim on SubmitOrder
    → REPLAY → return cached broker_submit_id
    broker_orders.order_ref UNIQUE constraint is a secondary guard
```

The `idempotency_records` table survives restarts. In-memory replay caches (`submitReplay`, `orderIdByKey`) are populated lazily from incoming requests and are intentionally best-effort — the DB is the source of truth.

---

## 6. Monitoring and Control Plane

All endpoints served by `monitoring-api` on HTTP :18084.

```
# Control
POST /api/v1/system/kill-switch     {"enabled": true|false}
POST /api/v1/system/trading-mode    {"trading_mode": "NORMAL"|"FROZEN"}

# Status
GET  /api/v1/system/health              → {tradingMode, killSwitch, serviceStatus}
GET  /api/v1/system/consistency-status  → cross-service state coherence check
GET  /api/v1/system/alerts              → recent system.alerts.v1 messages

# Streaming
GET  /api/v1/stream/events              → SSE stream of live events (all topics)

# Manual injection (bypass ingress validation in test/debug scenarios)
POST /api/v1/trade-events/manual
POST /api/v1/trade-events/external
```

State management:
1. HTTP request updates `AtomicReference<SystemControlState>` immediately (in-memory)
2. `persistControl()` writes to `system_controls` table — propagates exceptions (HTTP 500 on DB failure)
3. On restart: constructor reads `system_controls` and restores `killSwitch` + `tradingMode` before serving
4. `order-service` reads `tradingMode` from `OrderSafetyEngine` (in-memory, set by watchdog or control plane)

---

## 7. Structured Log Correlation

All 8 services emit logs in this format:

```
2026-03-06T06:30:00Z level= INFO service=risk-service
  trace_id=trc-aaa request_id=evt-bbb client_event_id=k-abc-123 principal_id=
  agent_id=agent-alpha signal_id=sig-xyz order_intent_id= instrument_id=AAPL
  [grpc-default-executor-0] c.a.s.risk.grpc.RiskDecisionGrpcService - ...
```

MDC keys are set at each entry point and cleared in `finally` blocks:

| Entry point | Keys set |
|---|---|
| HTTP handlers (`IngressController`) | `agent_id` |
| Kafka consumers (`EventProcessorConsumer`, `AgentRuntimeConsumer`, `FillsConsumer`, `AlertEventConsumer`) | `trace_id`, `client_event_id`, `request_id`, `agent_id`, `signal_id`, `instrument_id` |
| gRPC services (`RiskDecisionGrpcService`, `OrderCommandGrpcService`, `BrokerCommandGrpcService`) | `agent_id`, `signal_id` or `order_intent_id`, `instrument_id` |

Configured in each service's `src/main/resources/application.yml` under `logging.pattern.console`.
