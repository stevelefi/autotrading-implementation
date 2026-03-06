# autotrading-implementation

Production-grade paper-trading system: 8 microservices, contract-first gRPC+Kafka backbone, PostgreSQL persistence, full observability stack.

- **Spec baseline**: `spec-v1.0.1-m0m1` (pinned in `SPEC_VERSION.json`)
- **Build**: Java 21, Spring Boot 3.3.5, Maven multi-module (12 modules)
- **Status**: 12/12 modules BUILD SUCCESS — 19 e2e tests green (2026-03-06)

---

## Architecture Overview

```
External / Trader UI
        │  HTTP POST /api/v1/trade-events
        ▼
  ingress-gateway-service  ──► ingress.events.normalized.v1 (Kafka)
        │
        ▼
  event-processor-service  ──► trade.events.routed.v1 (Kafka)
        │
        ▼
  agent-runtime-service ──gRPC──► risk-service (port 9091)
                                        │
                                gRPC ──► order-service (port 9092)
                                                │
                                        gRPC ──► ibkr-connector-service (port 9093)
                                                        │
                                                fills.executed.v1 (Kafka)
                                                        │
                                                performance-service

  monitoring-api subscribes to system.alerts.v1 + risk.events.v1
```

Transport split:
- **gRPC** — real-time command path (agent-runtime → risk → order → ibkr-connector)
- **Kafka (Redpanda)** — event backbone, all inter-service events via outbox/inbox pattern

---

## Services

| Service | HTTP (local) | gRPC (local) | Role |
|---|---|---|---|
| ingress-gateway-service | 18080 | — | HTTP ingest, normalize, publish to Kafka |
| event-processor-service | 18085 | — | Route normalized events to `trade.events.routed.v1` |
| agent-runtime-service | 18086 | — | Consume routed events, drive risk gRPC |
| risk-service | 18081 | 19091 | Policy evaluation, persist decisions, publish audit events |
| order-service | 18082 | 19092 | Order lifecycle, safety engine, 60s timeout watchdog |
| ibkr-connector-service | 18083 | 19093 | Broker connector (IBKR / simulator), fill recording |
| performance-service | 18087 | — | Position + PnL projection from fill events |
| monitoring-api | 18084 | — | Kill-switch, trading-mode controls, SSE dashboard |

---

## Kafka Topics

| Topic | Producer | Consumer(s) |
|---|---|---|
| `ingress.events.normalized.v1` | ingress-gateway-service | event-processor-service |
| `ingress.errors.v1` | ingress-gateway-service | monitoring-api |
| `trade.events.routed.v1` | event-processor-service (via outbox) | agent-runtime-service |
| `orders.status.v1` | ibkr-connector-service (via outbox) | monitoring-api, order-service |
| `fills.executed.v1` | ibkr-connector-service (via outbox) | performance-service |
| `policy.evaluations.audit.v1` | risk-service (via outbox) | monitoring-api |
| `positions.updated.v1` | performance-service (via outbox) | monitoring-api |
| `pnl.snapshots.v1` | performance-service (via outbox) | monitoring-api |
| `risk.events.v1` | risk-service (via outbox) | monitoring-api |
| `system.alerts.v1` | order-service (via outbox) | monitoring-api |

All Kafka publishing goes through the **outbox table** (`outbox_events`) in the same transaction as the domain mutation. `OutboxPollerLifecycle` drains the table to Kafka.

---

## Reliability Guarantees

| Guarantee | Mechanism |
|---|---|
| At-least-once + idempotent delivery | `idempotency_records` table + `IdempotencyService` |
| Exactly-once consumer processing | `consumer_inbox` table + `ConsumerDeduper` |
| Transactional outbox | `outbox_events` persisted in same TX as domain mutation |
| Broker submit dedup | `broker_orders.order_ref` unique constraint + idempotency check |
| 60-second first-status watchdog | `OrderTimeoutWatchdogLifecycle` → `UNKNOWN_PENDING_RECON` + `FROZEN` |
| Persist-before-gRPC crash safety | Signal saved to DB before `riskStub.evaluateSignal()` |
| Crash-safe kill-switch | `system_controls` table restored on `MonitoringController` startup |

---

## Monorepo Layout

```
libs/
  contracts/          protobuf-generated gRPC contracts (Java)
  reliability-core/   idempotency, outbox/inbox, outbox poller, reliability metrics

services/
  ingress-gateway-service/
  event-processor-service/
  agent-runtime-service/
  risk-service/
  order-service/
  ibkr-connector-service/
  performance-service/
  monitoring-api/

db/migrations/        Flyway V1 + V2 SQL migrations (20 tables)
infra/local/          Docker Compose + env templates
infra/observability/  OTel, Prometheus, Loki, Grafana configs
infra/helm/           trading-service Helm chart
tests/e2e/            Cross-service and migration tests (19 tests)
reports/blitz/        Evidence pack (day reports, drill logs, smoke results)
scripts/              smoke_local.py, stack.py CLI
tools/                spec_sync.py
```

---

## Quick Start

### Prerequisites
- Java 21, Maven 3.9+
- Docker Desktop

### Run tests
```bash
make verify-spec        # verify spec baseline
make test-unit          # all unit + integration tests (12 modules)
make test-e2e           # cross-service e2e tests
make test-coverage-core # JaCoCo gate on reliability-core
```

### Bring up local stack
```bash
make up           # start all services + infra via docker-compose
make smoke-local  # run smoke test suite against local stack
make down         # tear down
```

### Python stack CLI
```bash
python3 scripts/stack.py up
python3 scripts/stack.py smoke
python3 scripts/stack.py down
```

### Spec sync (required before coding)
```bash
python3 tools/spec_sync.py sync \
  --repo-url https://github.com/stevelefi/autotrading.git \
  --ref spec-v1.0.1-m0m1 \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json

python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
```

---

## Observability

Full monitoring, tracing, and runbook guide: **[docs/OBSERVABILITY.md](docs/OBSERVABILITY.md)**

Local UIs after `make up`:

| Tool | URL | Purpose |
|---|---|---|
| Grafana | http://localhost:3000 | Reliability dashboard, Loki log search (Explore) |
| Prometheus | http://localhost:9090 | Ad-hoc PromQL, alert status, scrape targets |
| Redpanda Console | http://localhost:8888 | Kafka topic browser, consumer-group lag |
| Loki API | http://localhost:3100 | Log aggregation backend |
| OTel Collector gRPC | localhost:4317 | Trace/log ingestion endpoint |
| OTel Collector HTTP | localhost:4318 | Trace/log ingestion endpoint (HTTP/protobuf) |

**Trace a request across all services** by `trace_id`, `idempotency_key`, or `agent_id`:
```bash
python3 scripts/trace.py --trace-id trc-abc-123
python3 scripts/trace.py --idempotency-key k-abc-123 --since 30m
python3 scripts/trace.py --agent-id agent-alpha --service risk-service
```

**Reliability metrics** (via `/actuator/prometheus` on every service):
- `autotrading_reliability_outbox_backlog_age_ms`
- `autotrading_reliability_duplicate_suppression_count`
- `autotrading_reliability_first_status_timeout_count`

**Structured log fields** (all 8 services, usable as Loki filters):

| Field | What it identifies |
|---|---|
| `trace_id` | One end-to-end request through the full stack (per-attempt, OTel auto-generated) |
| `request_id` | Originating `X-Request-Id` header or Kafka event ID |
| `idempotency_key` | Caller-supplied dedup key — shared across retries of the same business request |
| `principal_id` | Actor who originated the trade (`X-Actor-Id` header) |
| `agent_id` | Trading agent driving this signal/order |
| `signal_id` | Links agent-runtime logs → risk-service logs for one decision |
| `order_intent_id` | Links risk → order-service → ibkr-connector logs for one order |
| `instrument_id` | Traded symbol — filters all activity on one symbol across services |

See [docs/OBSERVABILITY.md § 7](docs/OBSERVABILITY.md) for the join-chain diagram, `trace_id` vs `idempotency_key` explanation, and common troubleshooting workflows.

---

## Database

PostgreSQL 16 (`autotrading` DB). Schema managed by Flyway:

| Migration | Tables |
|---|---|
| V1 | `idempotency_records`, `outbox_events`, `consumer_inbox`, `ingress_raw_events`, `routed_trade_events`, `order_intents`, `order_ledger`, `executions`, `system_controls`, `reconciliation_runs` |
| V2 | `ingress_errors`, `signals`, `risk_decisions`, `risk_events`, `policy_decision_log`, `policy_bundle_history`, `order_state_history`, `broker_orders`, `positions`, `pnl_snapshots` |

Full schema: [db/migrations/V1__baseline.sql](db/migrations/V1__baseline.sql) · [db/migrations/V2__add_missing_tables.sql](db/migrations/V2__add_missing_tables.sql)

---

## Contributor Instructions

- Implementation workflow, PR checklist, spec freeze rules: [docs/IMPLEMENTATION_INSTRUCTIONS.md](docs/IMPLEMENTATION_INSTRUCTIONS.md)
- End-to-end data flow and design: [docs/DATA_FLOW.md](docs/DATA_FLOW.md)
- Monitoring, tracing, and runbooks: [docs/OBSERVABILITY.md](docs/OBSERVABILITY.md)
- Blitz change control and agent guardrails: [AGENTS.md](AGENTS.md)

## Slack Agent Status

- Caller workflow: `.github/workflows/agent-status.yml`
- Uses reusable workflow from `stevelefi/autotrading-devops`

Required GitHub secrets: `SLACK_BOT_TOKEN`, `SLACK_CHANNEL_ID_STATUS`  
Optional: `SLACK_ONCALL_GROUP_ID` (used for `BLOCKED` mentions)
