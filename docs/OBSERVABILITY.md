# Observability Guide

Monitoring, log correlation, and issue-tracing playbook for the autotrading system.

---

## 1. UI Quick Reference

After `make up`, all dashboards are available locally:

| Tool | URL | Purpose |
|---|---|---|
| **Grafana** | http://localhost:3000 | Reliability metrics dashboard, log-search Explore, alert status |
| **Prometheus** | http://localhost:9090 | Ad-hoc PromQL, raw alert states, scrape targets |
| **Redpanda Console** | http://localhost:8888 | Kafka topic browser, consumer-group lag, message inspector |
| **Loki API** | http://localhost:3100 | Log aggregation backend (query via Grafana Explore or `trace.py`) |
| **OTel Collector** | grpc:4317 / http:4318 | Receives traces+logs from all 8 services, forwards to Loki |

Grafana credentials: set in `.env` (`GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`); defaults to `admin`/`admin`.

---

## 2. Grafana

### 2.1 Reliability Dashboard

**URL**: http://localhost:3000/d/autotrading-reliability

Pre-built panels (auto-refresh every 15 s):

| Panel | Metric | Green/Red threshold |
|---|---|---|
| First-Status Timeout Count | `autotrading_reliability_first_status_timeout_count` | Red if > 0 — trading is FROZEN |
| Outbox Backlog Age (ms) | `autotrading_reliability_outbox_backlog_age_ms` | Red if > 60,000 ms |
| Duplicate Suppression Count | `autotrading_reliability_duplicate_suppression_count` | Per-service time-series |

### 2.2 Explore: Log Search

**URL**: http://localhost:3000/explore — select datasource **Loki**

Useful LogQL snippets:

```logql
# All logs for one service
{service="risk-service"}

# All logs across all autotrading services
{compose_project="autotrading"}

# Errors across all services
{compose_project="autotrading"} |= "ERROR"

# Trace a request end-to-end by trace_id
{compose_project="autotrading"} |= "trace_id=trc-abc-123"

# Follow a specific order intent
{compose_project="autotrading"} |= "order_intent_id=oi-xyz"

# Follow an idempotency key
{compose_project="autotrading"} |= "idempotency_key=k-abc-123"

# Follow an agent's activity
{compose_project="autotrading"} |= "agent_id=agent-alpha"

# gRPC errors only
{compose_project="autotrading"} |= "StatusRuntimeException"

# Outbox publish failures
{service="order-service"} |= "FAILED" |= "outbox"

# Parse structured fields for filtering (logfmt-style MDC in log lines)
{compose_project="autotrading"}
  | regexp `trace_id=(?P<trace_id>\S+).*idempotency_key=(?P<idempotency_key>\S+)`
  | trace_id = "trc-abc-123"
```

### 2.3 Explore: Prometheus Metrics

**URL**: http://localhost:3000/explore — select datasource **Prometheus**

```promql
# All services up (1) / down (0)
up{job=~".*-service|monitoring-api"}

# HTTP 5xx error rate per service (Spring Boot Actuator)
rate(http_server_requests_seconds_count{status=~"5.."}[1m])

# Outbox backlog age per service
autotrading_reliability_outbox_backlog_age_ms

# Duplicate suppression rate (last 5m)
increase(autotrading_reliability_duplicate_suppression_count[5m])

# JVM heap usage per service
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# gRPC method call rate
grpc_server_calls_total

# DB connection pool saturation
hikaricp_connections_active / hikaricp_connections_max
```

---

## 3. Prometheus UI

**URL**: http://localhost:9090

### Alert Status

Navigate to **Alerts** (top menu) to see current firing/pending alerts:

| Alert | Severity | Meaning |
|---|---|---|
| `FirstStatusTimeoutHigh` | critical | An order did not receive broker first-status within 60 s — tradingMode may be FROZEN |
| `OutboxBacklogStale` | warning | Oldest pending outbox event is >60 s old — Kafka/Redpanda connectivity issue |
| `DuplicateSuppressionCountRising` | warning | >10 deduplications in 5 min — upstream retry storm |

### Scrape Targets

Navigate to **Status → Targets** to verify all 8 service scrape endpoints are `UP`:

```
http://ingress-gateway-service:8080/actuator/prometheus
http://risk-service:8080/actuator/prometheus
http://order-service:8080/actuator/prometheus
http://ibkr-connector-service:8080/actuator/prometheus
http://event-processor-service:8080/actuator/prometheus
http://agent-runtime-service:8080/actuator/prometheus
http://performance-service:8080/actuator/prometheus
http://monitoring-api:8080/actuator/prometheus
```

---

## 4. Redpanda Console

**URL**: http://localhost:8888

### Topics Tab

View all 10 production topics + their message count and partition layout:

| Topic | Key flow |
|---|---|
| `ingress.events.normalized.v1` | ingress → event-processor |
| `trade.events.routed.v1` | event-processor → agent-runtime |
| `orders.status.v1` | ibkr-connector → monitoring-api, order-service |
| `fills.executed.v1` | ibkr-connector → performance-service |
| `policy.evaluations.audit.v1` | risk-service → monitoring-api |
| `positions.updated.v1` | performance-service → monitoring-api |
| `pnl.snapshots.v1` | performance-service → monitoring-api |
| `risk.events.v1` | risk-service → monitoring-api |
| `system.alerts.v1` | order-service → monitoring-api |
| `ingress.errors.v1` | ingress-gateway → monitoring-api |

Click a topic → **Messages** tab to browse individual messages with offsets and timestamps.

### Consumer Groups Tab

Check `Consumer Groups` to see per-topic lag for each consumer group. A non-zero and growing lag on `trade.events.routed.v1` can indicate `agent-runtime-service` is down or overloaded.

---

## 5. Per-Service Health Endpoints

Quick health check for all 8 services (from host after `make up`):

```bash
# Readiness (returns UP / DOWN, used in Docker healthcheck)
curl -s http://localhost:18080/actuator/health/readiness | jq .
curl -s http://localhost:18081/actuator/health/readiness | jq .
curl -s http://localhost:18082/actuator/health/readiness | jq .
curl -s http://localhost:18083/actuator/health/readiness | jq .
curl -s http://localhost:18084/actuator/health/readiness | jq .
curl -s http://localhost:18085/actuator/health/readiness | jq .
curl -s http://localhost:18086/actuator/health/readiness | jq .
curl -s http://localhost:18087/actuator/health/readiness | jq .

# All services in one script
for port in 18080 18081 18082 18083 18084 18085 18086 18087; do
  status=$(curl -s http://localhost:$port/actuator/health/readiness | jq -r '.status' 2>/dev/null || echo "UNREACHABLE")
  echo "port $port: $status"
done
```

---

## 6. CLI Trace Tool — `scripts/trace.py`

Query Loki from the terminal without opening Grafana. Useful for quick issue triage or CI assertions.

```bash
# Trace all services touched by a single trace_id
python3 scripts/trace.py --trace-id trc-abc-123

# Follow an idempotency key across all services
python3 scripts/trace.py --idempotency-key k-abc-123

# All activity for an agent in the last 30 minutes
python3 scripts/trace.py --agent-id agent-alpha --since 30m

# All activity for a specific order intent
python3 scripts/trace.py --order-intent-id oi-xyz-789

# Scope to a single service
python3 scripts/trace.py --trace-id trc-abc-123 --service risk-service

# Show only ERROR lines
python3 scripts/trace.py --trace-id trc-abc-123 --level ERROR

# Output raw JSON for piping
python3 scripts/trace.py --trace-id trc-abc-123 --json | jq '.[] | .line'

# Custom time range (default: last 1 hour)
python3 scripts/trace.py --idempotency-key k-abc-123 --since 2h

# Custom Loki URL (default: http://localhost:3100)
python3 scripts/trace.py --trace-id trc-abc-123 --loki-url http://loki.prod:3100
```

Output is chronological across all services, prefixed by timestamp and service name:

```
2026-03-06T06:30:01.123Z  ingress-gateway-service  [http-nio] INFO  ... trace_id=trc-abc-123 idempotency_key=k-abc-123 ...
2026-03-06T06:30:01.145Z  event-processor-service  [consumer-1] INFO  ... trace_id=trc-abc-123 ...
2026-03-06T06:30:01.201Z  agent-runtime-service    [consumer-1] INFO  ... trace_id=trc-abc-123 signal_id=sig-xyz ...
2026-03-06T06:30:01.310Z  risk-service             [grpc-exec-0] INFO  ... trace_id=trc-abc-123 agent_id=agent-alpha ...
2026-03-06T06:30:01.412Z  order-service            [grpc-exec-0] INFO  ... trace_id=trc-abc-123 order_intent_id=oi-xyz-789 ...
2026-03-06T06:30:01.530Z  ibkr-connector-service   [grpc-exec-0] INFO  ... trace_id=trc-abc-123 ...
```

---

## 7. Structured Log Fields (MDC)

All 8 services embed these fields in every log line via SLF4J MDC. Each field is a first-class Loki filter key.

| Field | What it identifies | Set by | Example |
|---|---|---|---|
| `trace_id` | One end-to-end request through the full stack — from HTTP POST to performance-service. Auto-generated by the OTel Java agent; consistent across all service hops for a single request. | All services (OTel auto-instrumentation) | `trc-abc-123` |
| `request_id` | The `X-Request-Id` HTTP header sent by the caller, or the event ID from the Kafka envelope. Allows correlating the originating HTTP call to downstream Kafka events. | HTTP handlers, Kafka consumers | `evt-bbb-456` |
| `idempotency_key` | The caller-supplied dedup key. Same key = same logical business request even across retries. Unlike `trace_id`, a retried call shares the same `idempotency_key` but gets a new `trace_id`. | Kafka consumers (from event envelope) | `k-abc-123` |
| `principal_id` | The actor/user who originated the trade event (`X-Actor-Id` HTTP header). Useful for audit trails and access-pattern investigation. | ingress-gateway-service | `user-xyz` |
| `agent_id` | Which trading agent drove this signal and order. Scopes all log lines for one agent's activity across agent-runtime, risk, and order services. | agent-runtime, risk, order (from gRPC request) | `agent-alpha` |
| `signal_id` | The specific signal that triggered evaluation. Links agent-runtime logs to risk-service logs for the same logical decision. | agent-runtime, risk-service | `sig-xyz` |
| `order_intent_id` | The order created by order-service. Links risk-service logs → order-service logs → ibkr-connector logs for a single order's full lifecycle. | order-service, ibkr-connector | `oi-xyz-789` |
| `instrument_id` | The traded symbol (e.g. `AAPL`). Filters all activity — risk evaluations, orders, fills, positions — for one symbol across all services. | risk, order, ibkr-connector | `AAPL` |

Fields are cleared in `finally` blocks on every thread — no MDC leakage between requests.

### 7.1 How the Fields Chain Together

A single trade flows through up to 6 services. The fields form a join chain that lets you narrow from symptom to root cause:

```
trace_id           → ALL log lines for one request, all services
  └─ idempotency_key  → was this a NEW, REPLAY, or CONFLICT?
       └─ signal_id       → jump from agent-runtime into risk-service
            └─ order_intent_id → jump from risk → order-service → ibkr-connector
                 └─ instrument_id → all activity on one symbol across all services
```

**`trace_id` vs `idempotency_key`**: `trace_id` is per-attempt (new value each retry); `idempotency_key` is per-business-request (shared across all retries). To understand whether a retry went through, filter by `idempotency_key` and look for `REPLAY` or `CONFLICT` outcomes.

### 7.2 Common Troubleshooting Workflows

```bash
# "Something went wrong — where in the stack did it fail?"
python3 scripts/trace.py --trace-id trc-abc-123
# Shows every service that touched this request, in time order

# "Did this retry actually get deduplicated or did it go through twice?"
python3 scripts/trace.py --idempotency-key k-abc-123 | grep -E "NEW|REPLAY|CONFLICT"

# "Risk denied something — why?"
python3 scripts/trace.py --signal-id sig-xyz --service risk-service

# "This order is stuck — what happened at the broker layer?"
python3 scripts/trace.py --order-intent-id oi-xyz-789 --service ibkr-connector-service

# "What has agent-alpha been doing in the last hour?"
python3 scripts/trace.py --agent-id agent-alpha --since 1h

# "Show all AAPL activity across all services"
python3 scripts/trace.py --instrument-id AAPL --since 30m

# "Who triggered this trade?"
python3 scripts/trace.py --trace-id trc-abc-123 | grep principal_id

# "Show only errors for this request"
python3 scripts/trace.py --trace-id trc-abc-123 --level ERROR
```

---

## 8. Alert Runbooks

### 8.1 `FirstStatusTimeoutHigh` (critical)

**Meaning**: `OrderTimeoutWatchdogLifecycle` detected that one or more orders did not receive a first broker status within 60 seconds. The system has automatically set `tradingMode = FROZEN`.

**Investigation**:
```bash
# 1. Check current trading mode
curl -s http://localhost:18084/api/v1/system/health | jq .

# 2. Find the frozen order in Loki
python3 scripts/trace.py --level WARN --service order-service --since 15m

# 3. Check DB for stuck orders
# (connect to postgres container)
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT id, order_intent_id, status, created_at_utc FROM order_ledger WHERE status='UNKNOWN_PENDING_RECON' ORDER BY created_at_utc DESC LIMIT 10;"

# 4. Inspect broker connectivity
curl -s http://localhost:18083/actuator/health | jq .
```

**Resolution**:
```bash
# Unfreeze trading mode (only after confirming broker connectivity)
curl -s -X POST http://localhost:18084/api/v1/system/trading-mode \
  -H "Content-Type: application/json" \
  -d '{"trading_mode": "NORMAL"}'

# Or enable kill-switch for full pause
curl -s -X POST http://localhost:18084/api/v1/system/kill-switch \
  -H "Content-Type: application/json" \
  -d '{"enabled": true}'
```

---

### 8.2 `OutboxBacklogStale` (warning)

**Meaning**: The oldest row in some service's `outbox_events` table has `status='NEW'` and hasn't been published to Kafka for >60 seconds. The `OutboxPollerLifecycle` has stalled or Redpanda is unreachable.

**Investigation**:
```bash
# 1. Check Redpanda health
curl -s http://localhost:18080/actuator/health | jq .components.kafka

# 2. Check outbox backlog in Prometheus
open http://localhost:9090/graph?g0.expr=autotrading_reliability_outbox_backlog_age_ms

# 3. Check DB for stuck outbox rows
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT topic, status, attempts, created_at_utc, published_at_utc
   FROM outbox_events
   WHERE status != 'DELIVERED'
   ORDER BY created_at_utc
   LIMIT 20;"

# 4. Check Redpanda Console for broker errors
open http://localhost:8888
```

**Resolution**:
```bash
# Restart the affected service (OutboxPoller restarts with it)
docker compose -f infra/local/docker-compose.yml restart <service-name>

# Check if Redpanda needs restart
docker compose -f infra/local/docker-compose.yml restart redpanda
```

---

### 8.3 `DuplicateSuppressionCountRising` (warning)

**Meaning**: `ConsumerDeduper` is seeing >10 duplicate event IDs in 5 minutes, indicating upstream services are replaying events.

**Investigation**:
```bash
# 1. Find duplicate events in Loki
python3 scripts/trace.py --service event-processor-service --since 10m | grep -i "duplicate\|replay\|REPLAY"

# 2. Check consumer group offsets for reset or rewind
open http://localhost:8888/groups

# 3. Check consumer_inbox table for oldest duplicates
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT consumer_name, count(*) as dup_count, max(created_at_utc)
   FROM consumer_inbox
   GROUP BY consumer_name
   ORDER BY dup_count DESC
   LIMIT 10;"
```

---

## 9. Common Issue Patterns

### Order stuck in `SUBMIT_REQUESTED`

The order was accepted by `order-service` and forwarded to `ibkr-connector-service` via gRPC, but no broker ack was recorded.

```bash
# Find the order_intent_id from logs
python3 scripts/trace.py --idempotency-key <key> --service order-service

# Check broker_orders table
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT id, order_intent_id, broker_submit_id, status, created_at_utc
   FROM broker_orders
   ORDER BY created_at_utc DESC LIMIT 5;"

# Check ibkr-connector logs for the submit attempt
python3 scripts/trace.py --order-intent-id <oi-id> --service ibkr-connector-service
```

---

### Trading frozen unexpectedly

```bash
# Check current system state
curl -s http://localhost:18084/api/v1/system/health | jq '{tradingMode, killSwitch}'

# Check alerts feed
curl -s http://localhost:18084/api/v1/system/alerts | jq .

# Check system_controls in DB (what was persisted on last change)
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT trading_mode, kill_switch_enabled, updated_at_utc, updated_by
   FROM system_controls
   ORDER BY updated_at_utc DESC LIMIT 5;"
```

---

### Outbox not draining (service-level)

```bash
# Check per-service outbox_events
docker exec -it $(docker ps -q -f name=postgres) psql -U $POSTGRES_USER -d $POSTGRES_DB -c \
  "SELECT source_service, topic, status, count(*)
   FROM outbox_events
   WHERE status != 'DELIVERED'
   GROUP BY source_service, topic, status;"
```

---

### Kafka consumer not making progress

```bash
# Check consumer group lag in Redpanda Console
open http://localhost:8888/groups

# Or via rpk CLI
docker exec redpanda rpk group describe <group-id>

# Common group IDs:
#   event-processor-group
#   agent-runtime-group
#   performance-group
#   monitoring-group
```

---

## 10. DB Inspection Quick Reference

Connect to Postgres:
```bash
docker exec -it $(docker ps -q -f name=postgres) \
  psql -U ${POSTGRES_USER:-atrader} -d ${POSTGRES_DB:-autotrading}
```

Useful queries:
```sql
-- Current trading mode
SELECT trading_mode, kill_switch_enabled, updated_at_utc FROM system_controls ORDER BY updated_at_utc DESC LIMIT 1;

-- Pending outbox events (backlog)
SELECT source_service, topic, status, attempts, created_at_utc
FROM outbox_events WHERE status != 'DELIVERED'
ORDER BY created_at_utc LIMIT 20;

-- Recent order states
SELECT o.id, o.agent_id, o.instrument_id, l.status, l.created_at_utc
FROM order_intents o JOIN order_ledger l ON l.order_intent_id = o.id
ORDER BY l.created_at_utc DESC LIMIT 10;

-- Stuck orders (UNKNOWN_PENDING_RECON or SUBMIT_REQUESTED older than 5 min)
SELECT id, order_intent_id, status, created_at_utc
FROM order_ledger
WHERE status IN ('SUBMIT_REQUESTED', 'UNKNOWN_PENDING_RECON')
  AND created_at_utc < NOW() - INTERVAL '5 minutes';

-- Recent fills
SELECT e.id, e.order_intent_id, e.quantity, e.price, e.commission, e.created_at_utc
FROM executions e ORDER BY e.created_at_utc DESC LIMIT 10;

-- idempotency conflicts
SELECT idempotency_key, outcome, created_at_utc
FROM idempotency_records WHERE outcome = 'CONFLICT'
ORDER BY created_at_utc DESC LIMIT 10;

-- Recent risk decisions
SELECT agent_id, instrument_id, decision, matched_rule_ids, created_at_utc
FROM risk_decisions ORDER BY created_at_utc DESC LIMIT 10;

-- Consumer inbox (check for duplicate suppression)
SELECT consumer_name, count(*) FROM consumer_inbox GROUP BY consumer_name;
```

---

## 11. Log Aggregation Pipeline

```
All 8 services (Spring Boot + OTel Java Agent)
    │
    ├──► OTel OTLP/HTTP (port 4318) ──► OTel Collector
    │                                       │
    │                                       ├── traces → debug exporter (OTel Collector stdout)
    │                                       └── logs   → Loki (http://loki:3100/otlp)
    │
    └──► Docker stdout/stderr
              │
              └── Promtail (Docker socket discovery)
                       │
                       └── Loki (http://loki:3100/loki/api/v1/push)
                                │
                                └── Grafana Explore  /  trace.py CLI
```

Both pipelines deliver to Loki; Promtail adds `service`, `container`, and `compose_project` labels from Docker metadata. OTel adds structured fields from the Java agent.

For distributed **trace** storage (linking spans across services), add Grafana Tempo to the compose stack (`otlphttp/tempo` exporter in `otel-collector/config.yaml`) and wire it as a Grafana datasource. The current setup uses the `debug` exporter, which emits trace summaries to the OTel Collector container log.

---

## 12. Quick Links

| | |
|---|---|
| Data flow and service interactions | [docs/DATA_FLOW.md](DATA_FLOW.md) |
| Implementation workflow, spec freeze | [docs/IMPLEMENTATION_INSTRUCTIONS.md](IMPLEMENTATION_INSTRUCTIONS.md) |
| Reliability drill logs | [reports/blitz/drill-logs/](../reports/blitz/drill-logs/) |
| Prometheus alerts config | [infra/observability/prometheus/alerts.yml](../infra/observability/prometheus/alerts.yml) |
| Grafana reliability dashboard | [infra/observability/grafana/dashboards/reliability.json](../infra/observability/grafana/dashboards/reliability.json) |
| OTel Collector config | [infra/observability/otel-collector/config.yaml](../infra/observability/otel-collector/config.yaml) |
| Promtail config | [infra/observability/promtail/promtail-config.yaml](../infra/observability/promtail/promtail-config.yaml) |
