# Reliability Drill Runbook

## Scenarios
1. Kafka outage and recovery
2. Restart with pending outbox rows
3. Duplicate callback replay
4. Broker first-status timeout (>60s) freeze path

## Acceptance
- No duplicate state transitions
- Outbox backlog drains after recovery
- Freeze mode transitions are auditable

## Local Execution Sequence
1. `make verify-spec`
2. `make test-unit`
3. `make test-e2e`
4. `make up`
5. `make smoke-local`
6. `make down`

## Smoke Validation Expectations
1. Ingress replay: same idempotency key + same payload returns same acceptance metadata.
2. Ingress conflict: same idempotency key + different payload returns conflict.
3. gRPC command retry: duplicate command path attempt does not produce duplicate broker submit.
4. Timeout drill: missing first broker status for 60 seconds drives:
   - order state `UNKNOWN_PENDING_RECON`
   - trading mode `FROZEN`
   - critical timeout alert event.

## Observability Validation
When stack is up, verify:
1. Prometheus target health: `http://localhost:9090/targets`
2. Grafana data sources/dashboards: `http://localhost:3000`
3. Reliability metrics queries:
   - `sum(autotrading_reliability_first_status_timeout_count)`
   - `max(autotrading_reliability_outbox_backlog_age_ms)`
   - `sum(autotrading_reliability_duplicate_suppression_count)`
4. Alert conditions defined in `infra/observability/prometheus/alerts.yml` evaluate without rule errors.

## Evidence Artifacts
- `reports/blitz/e2e-results/smoke-local-<timestamp>.md`
- `reports/blitz/drill-logs/smoke-local-<timestamp>.json`
- `reports/blitz/observability-results/*.json`
- `reports/blitz/observability-results/*.log`
