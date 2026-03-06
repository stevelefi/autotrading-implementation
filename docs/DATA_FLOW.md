# Data Flow

> **Design context**: For the canonical sequence diagrams, domain model, and consistency requirements that this flow implements, see [design/04-sequence-diagrams.md](https://github.com/stevelefi/autotrading/blob/main/docs/design/04-sequence-diagrams.md) and [ORDER\_CONSISTENCY\_AND\_RECONCILIATION.md](https://github.com/stevelefi/autotrading/blob/main/docs/ORDER_CONSISTENCY_AND_RECONCILIATION.md) in the spec repo.

## 1. Happy-Path Signal-to-Fill Flow

```
[Trader UI / External System]
    │
    │  HTTP POST /api/v1/trade-events/manual
    │  Headers: X-Actor-Id, X-Request-Id
    │  Body: { idempotency_key, agent_id, instrument_id, side, qty, ... }
    ▼
┌──────────────────────────────────┐
│    ingress-gateway-service       │  HTTP :18080
│                                  │
│  1. Normalize + validate         │
│  2. Persist → ingress_raw_events │
│  3. Append → outbox_events (TX)  │
│  4. OutboxPoller → Kafka         │
└──────────────┬───────────────────┘
               │  ingress.events.normalized.v1
               ▼
┌──────────────────────────────────┐
│   event-processor-service        │  HTTP :18085
│                                  │
│  1. ConsumerDeduper (consumer_inbox)
│  2. EventProcessorRouter.route() │
│  3. Persist → routed_trade_events│
│  4. Append → outbox_events (TX)  │
│  5. OutboxPoller → Kafka         │
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
│     - Append → outbox_events     │
│       (policy.evaluations.audit.v1)
│  4. gRPC CreateOrderIntent ──────┼──► order-service :19092
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
│     - Append → outbox_events     │
│       (orders.status.v1)         │
│  3. Return broker_submit_id      │
│                                  │
│  [Later — fill callback]         │
│  4. @Transactional:              │
│     - Persist → executions       │
│     - Append → outbox_events     │
│       (fills.executed.v1)        │
└──────────────┬───────────────────┘
               │  fills.executed.v1 (via outbox)
               ▼
┌──────────────────────────────────┐
│   performance-service            │  HTTP :18087
│                                  │
│  1. ConsumerDeduper              │
│  2. ConcurrentHashMap.compute()  │  atomic position update
│  3. Persist → positions          │
│  4. Persist → pnl_snapshots      │
│  5. Append → outbox_events       │
│     (positions.updated.v1,       │
│      pnl.snapshots.v1)           │
└──────────────────────────────────┘
```

---

## 2. Outbox / Inbox Pattern

All Kafka publishing in this system uses the transactional outbox pattern:

```
Domain code  (@Transactional)
    │
    ├─ repository.save(domainEntity)    ─┐
    └─ outboxRepository.append(event)   ─┘  committed atomically
               │
               │  outbox_events table (status=NEW)
               │
       OutboxPollerLifecycle  (background thread, all services)
               │  SELECT ... WHERE status='NEW' ORDER BY created_at_utc
               ▼
       KafkaOutboxPublisher.publish()
               │  on success → UPDATE status='DELIVERED'
               │  on failure → UPDATE status='FAILED', attempts++
               ▼
       Kafka topic (Redpanda)
```

Consumer side (all `@KafkaListener` methods):

```
Kafka record
    ▼
@KafkaListener + @Transactional
    │
    ├─ ConsumerDeduper.runOnce(consumerName, eventId, work)
    │       │
    │       ├─ INSERT INTO consumer_inbox (consumer_name, event_id)
    │       │    conflict → skip (already processed, no-op)
    │       │
    │       └─ work.run()  ← domain logic in same TX
    │
    └─ commit offset on success, re-queue on failure (re-throws)
```

Guarantees:
- Publishing never happens without the domain mutation succeeding
- A consumer crash before TX commit replays the record (no data loss)
- A replayed record hits `ConsumerDeduper` — exactly-once processing

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
        risk-service persists risk_decisions, policy_decision_log, audit outbox event
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

## 5. Idempotency Key Flow

```
Client sends idempotency_key = "k-abc-123"

ingress-gateway-service:
    claim({key="k-abc-123", payloadHash=sha(body)})
    → NEW     → proceed, persist, publish
    → REPLAY  → return cached response (DUPLICATE status)
    → CONFLICT → reject (same key, different payload hash)

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
  trace_id=trc-aaa request_id=evt-bbb idempotency_key=k-abc-123 principal_id=
  agent_id=agent-alpha signal_id=sig-xyz order_intent_id= instrument_id=AAPL
  [grpc-default-executor-0] c.a.s.risk.grpc.RiskDecisionGrpcService - ...
```

MDC keys are set at each entry point and cleared in `finally` blocks:

| Entry point | Keys set |
|---|---|
| HTTP handlers (`IngressController`) | `agent_id` |
| Kafka consumers (`EventProcessorConsumer`, `AgentRuntimeConsumer`, `FillsConsumer`, `AlertEventConsumer`) | `trace_id`, `idempotency_key`, `request_id`, `agent_id`, `signal_id`, `instrument_id` |
| gRPC services (`RiskDecisionGrpcService`, `OrderCommandGrpcService`, `BrokerCommandGrpcService`) | `agent_id`, `signal_id` or `order_intent_id`, `instrument_id` |

Configured in each service's `src/main/resources/application.yml` under `logging.pattern.console`.
