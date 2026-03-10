# autotrading-implementation

Production-grade paper-trading system: 8 microservices, contract-first gRPC + Kafka backbone,
PostgreSQL persistence, and a full observability stack.

| Item | Value |
|------|-------|
| Spec baseline | `spec-v1.0.1-m0m1` (pinned in `SPEC_VERSION.json`) |
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Build | Maven multi-module — 12 modules — all `BUILD SUCCESS` |
| E2E tests | 41 green (2026-03-09) |
| Persistence | Spring Data JDBC + PostgreSQL 16 (9 Flyway migrations) |
| Messaging | Kafka via Redpanda (local) |
| RPC | gRPC 1.66.0 + Protobuf |

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Services](#services)
3. [System Flow](#system-flow)
4. [Data Flow](#data-flow)
5. [Kafka Topics](#kafka-topics)
6. [Reliability Guarantees](#reliability-guarantees)
7. [Database](#database)
8. [Tracing and Observability](#tracing-and-observability)
9. [Python Script Helpers](#python-script-helpers)
10. [Quick Start](#quick-start)
11. [Monorepo Layout](#monorepo-layout)
12. [Contributor Instructions](#contributor-instructions)

---

## Architecture Overview

```
External / Trader UI
        |  HTTP POST /api/v1/trade-events
        v
  ingress-gateway-service  --kafka-->  ingress.events.normalized.v1
        |
  event-processor-service  --kafka-->  trade.events.routed.v1
        |
  agent-runtime-service  --gRPC-->  risk-service :19091
                                          |
                                    gRPC -->  order-service :19092
                                                    |
                                            gRPC -->  ibkr-connector-service :19093
                                                            |
                                                    fills.executed.v1  (Kafka)
                                                            |
                                                    performance-service

  monitoring-api  subscribes to  system.alerts.v1 + risk.events.v1
```

Transport split:
- **gRPC** — synchronous command path: agent-runtime → risk → order → ibkr-connector
- **Kafka (Redpanda)** — event backbone: all inter-service event publishing via outbox/inbox pattern

---

## Services

| Service | HTTP (local) | gRPC (local) | Role |
|---------|-------------|-------------|------|
| `ingress-gateway-service` | 18080 | — | HTTP ingest, normalize, idempotency dedup, publish to Kafka |
| `event-processor-service` | 18085 | — | Route normalized events → `trade.events.routed.v1` |
| `agent-runtime-service` | 18086 | — | Consume routed events, drive risk gRPC |
| `risk-service` | 18081 | 19091 | Policy evaluation, persist decisions, publish audit events |
| `order-service` | 18082 | 19092 | Order lifecycle, safety engine, 60 s timeout watchdog |
| `ibkr-connector-service` | 18083 | 19093 | Broker connector (IBKR / simulator), fill recording, health state |
| `performance-service` | 18087 | — | Position + PnL projection from fill events |
| `monitoring-api` | 18084 | — | Kill-switch, trading-mode controls, SSE dashboard |

---

## System Flow

For the rendered Mermaid flowchart see **[docs/SYSTEM_FLOW.md](docs/SYSTEM_FLOW.md)**.

Text summary of the end-to-end request path:

```
(1) HTTP POST /api/v1/trade-events
        |
(2) ingress-gateway-service
    - normalize + validate
    - idempotency claim (client_event_id)
    - persist -> ingress_raw_events
    - afterCommit() -> KafkaFirstPublisher
        |  ingress.events.normalized.v1
(3) event-processor-service
    - ConsumerDeduper (consumer_inbox)
    - route event
    - persist -> routed_trade_events
        |  trade.events.routed.v1
(4) agent-runtime-service
    - ConsumerDeduper
    - persist -> signals  (FIRST, before gRPC -- crash safe)
        |  gRPC EvaluateSignal
(5) risk-service
    - validateLineage()
    - SimplePolicyEngine.evaluate() -> APPROVE / DENY
    - persist -> risk_decisions, policy_decision_log
    - best-effort publish -> policy.evaluations.audit.v1
        |  gRPC CreateOrderIntent
(6) order-service
    - check tradingMode != FROZEN
    - check brokerHealthCache.isUp()
    - idempotency claim
    - persist -> order_intents, order_ledger, order_state_history
    - OrderTimeoutWatchdog (60 s poll)
        |  gRPC SubmitOrder
(7) ibkr-connector-service
    - idempotency claim
    - persist -> broker_orders
    - publish -> orders.status.v1
    - return broker_submit_id
        |  fills.executed.v1  (async, later)
(8) performance-service
    - ConsumerDeduper
    - atomic position update (ConcurrentHashMap.compute())
    - persist -> positions, pnl_snapshots
```

Key design decisions:

- **Two Kafka hops then synchronous gRPC** — hot path latency ~112 ms total
- **Persist-before-gRPC** — agent-runtime saves the signal to DB *before* calling risk,
  so a crash before the response is safe to replay
- **Broker health gate** — ingress-gateway and order-service both check `BrokerHealthCache`
  before forwarding; new orders are rejected when the broker is `DOWN`
- **60 s watchdog** — `OrderTimeoutWatchdogLifecycle` polls every 5 s; if no ack within 60 s
  it sets `tradingMode = FROZEN` and publishes a `CRITICAL` alert on `system.alerts.v1`

---

## Data Flow

For the full annotated flow with code-level detail see **[docs/DATA_FLOW.md](docs/DATA_FLOW.md)**.

### Kafka-First Publishing (ingress-gateway only)

```
IngressService.accept()  @Transactional
    |- rawEventRepository.save()
    +- idempotencyService.markCompleted()

afterCommit() -> KafkaFirstPublisher
    |- Kafka OK  -> delivered to ingress.events.normalized.v1
    +- Kafka fail -> outbox_events  (PROPAGATION_REQUIRES_NEW TX)
              +- OutboxPollerLifecycle (500 ms) -> retry with doubling back-off
```

All other services publish directly (`DirectKafkaPublisher`) and roll back on Kafka failure,
relying on the uncommitted offset for re-delivery.

### Consumer-Side Deduplication (all services)

```
Kafka record -> @KafkaListener @Transactional
    +- ConsumerDeduper.runOnce(consumerName, eventId, work)
            |- INSERT consumer_inbox  -> conflict = already processed -> skip
            +- work.run()  in same TX
```

### Broker Health Guard (ingress-gateway + order-service)

Both services hold a `BrokerHealthCache` populated live by `IbkrHealthProbe`. When the cache
reports `DOWN`, calls to the command path are rejected immediately:

```
IngressService / OrderSafetyEngine
    +- brokerHealthCache.isUp()
           |- true  -> proceed
           +- false -> reject (SERVICE_UNAVAILABLE / FAILED_PRECONDITION)
```

`BrokerHealthPersister` persists each UP/DOWN transition to `broker_health_status`,
giving the monitoring-api a persistent audit trail of broker connectivity events.

---

## Kafka Topics

| Topic | Producer | Consumer(s) | Purpose |
|-------|----------|-------------|---------|
| `ingress.events.normalized.v1` | ingress-gateway | event-processor | Validated, normalized event after idempotency check |
| `ingress.errors.v1` | ingress-gateway | _(observability)_ | Failed / rejected ingress events |
| `trade.events.routed.v1` | event-processor | agent-runtime | Routed event ready for signal generation |
| `policy.evaluations.audit.v1` | risk-service | _(audit)_ | Full audit record of every risk evaluation |
| `risk.decisions.v1` | risk-service | _(observability)_ | ALLOW/DENY decision summary |
| `orders.status.v1` | ibkr-connector | order-service, monitoring-api | Broker status updates (SUBMITTED, FILLED, etc.) |
| `fills.executed.v1` | ibkr-connector | performance-service | Fill events for P&L and position tracking |
| `system.alerts.v1` | order-service | monitoring-api | System-level alerts (FROZEN, kill-switch) |
| `positions.updated.v1` | performance-service | _(downstream)_ | Real-time position changes |
| `pnl.snapshots.v1` | performance-service | _(downstream)_ | Point-in-time P&L snapshots |

All publishing uses `DirectKafkaPublisher` with doubling back-off (max 5 s budget).
`OutboxPollerLifecycle` (500 ms interval) runs only in `ingress-gateway-service`.

---

## Reliability Guarantees

| Guarantee | Mechanism |
|-----------|-----------|
| At-least-once + idempotent delivery | `idempotency_records` + `IdempotencyService` |
| Exactly-once consumer processing | `consumer_inbox` + `ConsumerDeduper` |
| Transactional outbox | `outbox_events` in same TX as domain write (ingress only) |
| Broker submit dedup | `broker_orders.order_ref` unique constraint |
| 60 s first-status watchdog | `OrderTimeoutWatchdogLifecycle` -> `UNKNOWN_PENDING_RECON` + `FROZEN` |
| Persist-before-gRPC safety | Signal saved before `riskStub.evaluateSignal()` |
| Crash-safe kill-switch | `system_controls` restored on `MonitoringController` startup |
| Broker health gate | `BrokerHealthCache` checked by ingress + order before connector call |
| Broker health persistence | `BrokerHealthPersister` writes UP/DOWN transitions to `broker_health_status` |

---

## Database

PostgreSQL 16 (`autotrading` database). Schema managed by Flyway — 9 migrations:

| Migration | What it adds |
|-----------|-------------|
| V1 | `idempotency_records`, `outbox_events`, `consumer_inbox`, `ingress_raw_events`, `routed_trade_events`, `order_intents`, `order_ledger`, `executions`, `system_controls`, `reconciliation_runs` |
| V2 | `ingress_errors`, `signals`, `risk_decisions`, `risk_events`, `policy_decision_log`, `policy_bundle_history`, `order_state_history`, `broker_orders`, `positions`, `pnl_snapshots` |
| V3 | Outbox retry back-off columns (`attempts`, `next_retry_at`) |
| V4 | `executions.broker_order_id` column |
| V5 | Fill missing columns across several tables |
| V6 | Drop cross-service foreign keys (service boundary isolation) |
| V7 | Convert `jsonb` columns to `TEXT` (Spring Data JDBC Rule 2) |
| V8 | Rename `idempotency_key` -> `client_event_id`; `ingress_event_id` -> `event_id` |
| V9 | `broker_health_status` — shared broker health state; seed row `broker_id='ibkr'` |

Full schema: [db/migrations/](db/migrations/)

### Tables by Service

| Service | Tables |
|---------|--------|
| ingress-gateway | `idempotency_records`, `ingress_raw_events`, `outbox_events` |
| event-processor | `consumer_inbox`, `routed_trade_events` |
| agent-runtime | `consumer_inbox`, `signals` |
| risk-service | `risk_decisions`, `risk_events`, `policy_decision_log` |
| order-service | `idempotency_records`, `order_intents`, `order_ledger`, `order_state_history`, `system_controls` |
| ibkr-connector | `idempotency_records`, `broker_orders`, `executions`, `broker_health_status` (R+W) |
| performance | `positions`, `pnl_snapshots`, `executions` (R) |
| monitoring-api | `system_controls` (R), `reconciliation_runs` |

---

## Tracing and Observability

Full guide: **[docs/OBSERVABILITY.md](docs/OBSERVABILITY.md)**

### Local UIs (after `make up`)

| Tool | URL | Purpose |
|------|-----|---------|
| Grafana | http://localhost:3000 | Reliability dashboard, Loki log search, Tempo trace viewer |
| Prometheus | http://localhost:9090 | PromQL, alert status, scrape targets |
| Redpanda Console | http://localhost:8888 | Kafka topic browser, consumer-group lag |
| Loki | http://localhost:3100 | Log aggregation backend |
| Tempo | http://localhost:3200 | Distributed trace storage — service waterfall by `trace_id` |
| OTel Collector | grpc:4317 / http:4318 | Trace + log ingestion from all 8 services |

### MDC Log Fields

All 8 services emit these fields in every log line. Each is a first-class Loki filter key.

| Field | What it identifies |
|-------|--------------------|
| `trace_id` | One end-to-end request through the full stack. Auto-generated by the OTel Java agent; consistent across all service hops for one attempt. New value on every retry. |
| `request_id` | Originating `X-Request-Id` HTTP header or Kafka event ID. |
| `client_event_id` | Caller-supplied dedup key. Same key = same logical business request, even across retries. Unlike `trace_id`, a retried call shares the same `client_event_id` but gets a new `trace_id`. |
| `principal_id` | Actor who originated the trade (`X-Actor-Id` header). |
| `agent_id` | Trading agent driving this signal and order. |
| `signal_id` | Links agent-runtime logs -> risk-service logs for one decision. |
| `order_intent_id` | Links risk -> order-service -> ibkr-connector logs for one order lifecycle. |
| `instrument_id` | Traded symbol. Filters all activity on one symbol across all services. |

### MDC Join Chain

```
trace_id             -> ALL log lines for one attempt, all services
  +- client_event_id    -> was this NEW, REPLAY, or CONFLICT?
       +- signal_id        -> jump from agent-runtime into risk-service
            +- order_intent_id -> jump from risk -> order -> ibkr-connector
                 +- instrument_id  -> all activity on one symbol
```

`trace_id` is per-attempt. `client_event_id` is per-business-request. To understand whether a
retry was deduplicated, filter by `client_event_id` and look for `REPLAY` or `CONFLICT` outcomes.

### Trace a Request from the CLI

```bash
# Follow one request end-to-end (all services, time-ordered)
python3 scripts/trace.py --trace-id trc-abc-123

# Follow a business request across all retries
python3 scripts/trace.py --client-event-id k-abc-123

# All activity for an agent in the last 30 min
python3 scripts/trace.py --agent-id agent-alpha --since 30m

# Follow a signal through risk evaluation
python3 scripts/trace.py --signal-id sig-xyz --service risk-service

# Follow an order intent through order-service and broker
python3 scripts/trace.py --order-intent-id oi-xyz-789

# All AAPL activity across all services
python3 scripts/trace.py --instrument-id AAPL --since 1h

# Show only errors
python3 scripts/trace.py --trace-id trc-abc-123 --level ERROR

# Machine-readable JSON for piping
python3 scripts/trace.py --client-event-id k-abc-123 --json | jq '.[] | .line'
```

---

## Python Script Helpers

All scripts live in `scripts/`. Run from the repo root with `python3 scripts/<name>.py`.

---

### `scripts/stack.py` — Local Stack Manager

Wraps `docker compose` to manage the full local stack (25 containers).

```bash
# Full stack
python3 scripts/stack.py up              # start infra + all 8 app services
python3 scripts/stack.py down            # stop everything + remove volumes

# Iterative development (keep infra running, only restart app)
python3 scripts/stack.py infra-up        # start postgres, redpanda, observability
python3 scripts/stack.py app-up          # start 8 app services (requires infra up)
python3 scripts/stack.py restart-app     # stop app -> rebuild images -> start app
python3 scripts/stack.py app-down        # stop app only (infra stays)
python3 scripts/stack.py build           # rebuild app Docker images without starting

# Inspection
python3 scripts/stack.py status          # show running containers
python3 scripts/stack.py logs            # tail all service logs
python3 scripts/stack.py logs --service risk-service   # tail one service

# CI / validation
python3 scripts/stack.py validate        # status + smoke suite
python3 scripts/stack.py ci             # full clean run: down -> build -> up -> validate -> down
```

**Fast iteration pattern** (avoids the ~2 min Flyway + Redpanda init on each code change):
```bash
python3 scripts/stack.py infra-up        # once per dev session
# ...edit code...
python3 scripts/stack.py restart-app     # after each code change
python3 scripts/stack.py down            # end of session
```

---

### `scripts/smoke_local.py` — 5-Phase Smoke Suite

Runs against the live stack (requires `stack.py up` first). Any phase failure exits non-zero.

```bash
python3 scripts/smoke_local.py
```

| Phase | What it validates |
|-------|-------------------|
| 1 — Readiness | All 8 services return `{"status":"UP"}` (360 s timeout) |
| 2 — Ingress idempotency | Duplicate `client_event_id` -> 202 with same `event_id`; conflicting payload on same key -> 202 replaying original |
| 3 — Command path | Risk -> Order -> IBKR; two identical risk calls produce exactly one broker submit |
| 4 — Timeout freeze drill | 60 s watchdog triggers `trading_mode=FROZEN`; alert present on `system.alerts.v1` |
| 5 — Async Kafka pipeline | End-to-end: ingress POST -> broker `total_submit_count` increments within 90 s |

Reports written to:
- `reports/blitz/e2e-results/smoke-local-<timestamp>.md` — human-readable pass/fail
- `reports/blitz/drill-logs/smoke-local-<timestamp>.json` — machine-readable detail

---

### `scripts/check.py` — Pre-Commit Gate

Runs all 8 required checks and prints a pass/fail summary. **All must be green before committing.**

```bash
python3 scripts/check.py                       # full gate (all 8 checks)
python3 scripts/check.py --fast                # skip e2e (checks 1-5, 7-8)
python3 scripts/check.py --skip-helm           # skip Helm checks
python3 scripts/check.py --only unit coverage  # run specific checks by name
```

| # | Name | What runs |
|---|------|-----------|
| 1 | `branch-check` | Branch name follows GitHub flow naming convention |
| 2 | `spec-verify` | Pinned spec baseline is current |
| 3 | `agent-sync` | `CLAUDE.md` is a symlink to `AGENTS.md` |
| 4 | `unit` | Reinstall contracts, then `mvn -B -DskipITs=true test` |
| 5 | `coverage` | `mvn -B -Pcoverage-core` on 5 core modules — minimum 50% line |
| 6 | `e2e` | `mvn -B -pl tests/e2e -am test` — all 5 e2e test classes (41 tests) |
| 7 | `helm-lint` | `helm lint infra/helm/charts/trading-service` |
| 8 | `helm-template` | `helm template` dry-run render |

---

### `scripts/test.py` — Maven Test Runner

```bash
python3 scripts/test.py unit                                  # all unit tests (all modules)
python3 scripts/test.py unit --module services/risk-service   # single module only
python3 scripts/test.py coverage                              # JaCoCo gate on 5 core modules
python3 scripts/test.py e2e                                   # all 5 e2e test classes
python3 scripts/test.py all                                   # unit + coverage + e2e (fail fast)
python3 scripts/test.py all --no-fail-fast                    # run all even if one fails
```

---

### `scripts/trace.py` — Loki Log Tracer

Queries Loki from the terminal. Output is chronological across all services.

```bash
# Filter by MDC field
python3 scripts/trace.py --trace-id trc-abc-123
python3 scripts/trace.py --client-event-id k-abc-123
python3 scripts/trace.py --signal-id sig-xyz
python3 scripts/trace.py --order-intent-id oi-xyz-789
python3 scripts/trace.py --agent-id agent-alpha
python3 scripts/trace.py --instrument-id AAPL
python3 scripts/trace.py --request-id evt-bbb-456

# Scope
python3 scripts/trace.py --agent-id agent-alpha --service risk-service
python3 scripts/trace.py --trace-id trc-abc-123 --level ERROR
python3 scripts/trace.py --client-event-id k-abc-123 --since 2h

# Output
python3 scripts/trace.py --trace-id trc-abc-123 --json | jq '.[] | .line'
python3 scripts/trace.py --trace-id trc-abc-123 --loki-url http://loki.prod:3100
python3 scripts/trace.py --agent-id agent-alpha --verbose     # print the LogQL query
```

Default `--since` is `1h`. Supported durations: `30s`, `5m`, `2h`, `1d`, etc.

Sample output:
```
2026-03-09T04:21:01.123Z  ingress-gateway-service  INFO  ... client_event_id=k-abc-123 trace_id=trc-...
2026-03-09T04:21:01.201Z  agent-runtime-service    INFO  ... signal_id=sig-xyz ...
2026-03-09T04:21:01.310Z  risk-service             INFO  ... order_intent_id=oi-xyz-789 ...
2026-03-09T04:21:01.412Z  order-service            INFO  ... order_intent_id=oi-xyz-789 ...
2026-03-09T04:21:01.530Z  ibkr-connector-service   INFO  ... broker_submit_id=bsub-... ...
```

---

### `scripts/branch_check.py` — Branch Name Validator

```bash
python3 scripts/branch_check.py                    # check current branch
python3 scripts/branch_check.py feature/my-topic   # check a specific name
```

Valid patterns: `feature/<desc>`, `bugfix/<desc>`, `hotfix/<desc>`, `chore/<desc>`,
`release/<desc>`, `AT-<NNNN>-<desc>`, `main`, `develop`.

---

### `scripts/pr.py` — Safe Branch + Commit + Push + PR

```bash
python3 scripts/pr.py \
  --branch feature/my-topic \
  --title "feat(risk): add new policy rule" \
  --body "Closes #42"

python3 scripts/pr.py --commit-only ...   # commit only, skip push + PR creation
python3 scripts/pr.py --push-only ...     # push + create PR, skip commit
```

---

### `scripts/load_20_orders.py` — Load Test Helper

Sends 20 trade events to the local ingress gateway for smoke validation and manual perf testing.

```bash
python3 scripts/load_20_orders.py
```

---

### `tools/spec_sync.py` — Spec Sync

```bash
# Sync (downloads spec docs into specs/vendor)
python3 tools/spec_sync.py sync \
  --repo-url https://github.com/stevelefi/autotrading.git \
  --ref spec-v1.0.1-m0m1 \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json

# Verify current baseline matches the pinned version
python3 tools/spec_sync.py verify \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json
```

---

## Quick Start

### Prerequisites

- Java 21 + Maven 3.9+
- Docker Desktop

### 1. Verify spec baseline

```bash
python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
```

### 2. Run tests

```bash
python3 scripts/test.py unit
python3 scripts/test.py coverage
python3 scripts/test.py e2e
```

### 3. Bring up local stack and run smoke

```bash
python3 scripts/stack.py up
python3 scripts/smoke_local.py      # must exit 0 before any PR
python3 scripts/stack.py down
```

### 4. Pre-commit gate (all 8 checks)

```bash
python3 scripts/check.py
```

---

## Monorepo Layout

```
libs/
  contracts/          protobuf-generated gRPC stubs (Java)
  reliability-core/   idempotency, outbox/inbox, outbox poller, broker health cache

services/
  ingress-gateway-service/   HTTP ingest + idempotency + Kafka-first publish
  event-processor-service/   Kafka routing + inbox dedup
  agent-runtime-service/     Strategy + signal generation
  risk-service/              Policy evaluation via gRPC
  order-service/             Order lifecycle + 60 s watchdog + safety engine
  ibkr-connector-service/    Broker connector + fill tracking + health probe
  performance-service/       P&L + position tracking
  monitoring-api/            Control plane (trading-mode, kill-switch, SSE)

db/migrations/        Flyway V1-V9 SQL migrations (21 tables)
infra/local/          Docker Compose + env templates
infra/observability/  OTel Collector, Prometheus, Loki, Grafana, Tempo configs
infra/helm/           trading-service Helm chart
tests/e2e/            41 cross-service + migration tests (5 test classes)
reports/blitz/        Evidence pack (day reports, drill logs, smoke results)
scripts/              stack.py, smoke_local.py, check.py, test.py, trace.py, pr.py, branch_check.py
tools/                spec_sync.py
```

---

## Contributor Instructions

| Topic | Document |
|-------|----------|
| Implementation workflow, spec freeze, PR checklist | [docs/IMPLEMENTATION_INSTRUCTIONS.md](docs/IMPLEMENTATION_INSTRUCTIONS.md) |
| End-to-end data flow, Kafka-first publish, gRPC command chain | [docs/DATA_FLOW.md](docs/DATA_FLOW.md) |
| System flow Mermaid diagram, Kafka topics, DB tables per service | [docs/SYSTEM_FLOW.md](docs/SYSTEM_FLOW.md) |
| Monitoring UIs, Loki LogQL, trace.py, alert runbooks, DB inspection | [docs/OBSERVABILITY.md](docs/OBSERVABILITY.md) |
| Blitz change control and AI agent guardrails | [AGENTS.md](AGENTS.md) |
| Reliability drill runbooks | [docs/runbooks/reliability-drills.md](docs/runbooks/reliability-drills.md) |

### Slack Agent Status

Caller workflow: `.github/workflows/agent-status.yml`
Uses reusable workflow from `stevelefi/autotrading-devops`

Required GitHub secrets: `SLACK_BOT_TOKEN`, `SLACK_CHANNEL_ID_STATUS`
Optional: `SLACK_ONCALL_GROUP_ID` (used for `BLOCKED` mentions)
