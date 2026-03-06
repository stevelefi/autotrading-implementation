# Day 4 Hardening Report

## Scope
- Runtime enablement completed for all paper-slice services using real container builds in local compose.
- OSS-first observability stack added for local/dev: OpenTelemetry collector, Prometheus, Loki, Promtail, Grafana.
- Validation performed against pinned spec baseline `spec-v1.0.1-m0m1` with contract freeze preserved.

## Gates Executed
- `make verify-spec`
- `make test-unit`
- `make test-e2e`
- `helm lint infra/helm/charts/trading-service`
- `helm template trading-service infra/helm/charts/trading-service -f infra/helm/charts/trading-service/values.yaml`
- `make up`
- `make smoke-local`
- Prometheus alert rule load and query checks

## Runtime Drill Outcome
- Compose services healthy: ingress, event-processor, agent-runtime, risk, order, ibkr-connector, performance, monitoring, postgres, redpanda, ibkr-simulator.
- Observability services healthy: otel-collector, prometheus, loki, promtail, grafana.
- Smoke harness scenarios passing:
  - ingress idempotency replay (`same key/same payload`)
  - ingress conflict (`same key/different payload`)
  - risk -> order -> broker command path dedupe on retry
  - 60s timeout path to `UNKNOWN_PENDING_RECON` + `FROZEN` + critical alert event
- Reliability telemetry exported and queryable:
  - `autotrading_reliability_outbox_backlog_age_ms`
  - `autotrading_reliability_duplicate_suppression_count`
  - `autotrading_reliability_first_status_timeout_count`
- Correlation logging baseline active for HTTP + gRPC:
  - `trace_id`
  - `request_id`
  - `idempotency_key`
  - `principal_id`

## Evidence
- `reports/blitz/e2e-results/smoke-local-20260306T034350Z.md`
- `reports/blitz/drill-logs/smoke-local-20260306T034350Z.json`
- `reports/blitz/observability-results/*.json`
- `reports/blitz/observability-results/*.log`
