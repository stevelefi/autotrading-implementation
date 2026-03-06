# autotrading-implementation

Paper-trading vertical-slice implementation monorepo aligned to the pinned spec baseline.

## Production Standard
- Contract-first development: no runtime API/topic/proto/schema drift from pinned spec.
- Reliability-first behavior: idempotency, outbox/inbox, freeze safety, and auditability are mandatory.
- Evidence-based release gates: unit/integration/e2e + migration + Helm validation must pass.
- Source control hygiene: generated and local-only artifacts must stay ignored by `.gitignore`.

## Spec Baseline
- Spec repo: `https://github.com/stevelefi/autotrading.git`
- Pinned ref: `spec-v1.0.1-m0m1`
- Local sync path (generated, gitignored): `specs/vendor/docs`

## Required Pre-Work (Stage 0)
```bash
python3 tools/spec_sync.py sync \
  --repo-url https://github.com/stevelefi/autotrading.git \
  --ref spec-v1.0.1-m0m1 \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json

python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
```

## Monorepo Layout
- `libs/contracts` protobuf-generated gRPC contracts
- `libs/reliability-core` envelope, idempotency, outbox/inbox, reliability metrics
- `services/*` ingress, routing, agent runtime, risk, order, broker connector, performance, monitoring
- `db/migrations` SQL baseline
- `infra/local` Docker Compose and env templates
- `infra/observability` OTel collector, Prometheus, Loki, Grafana configs
- `infra/helm` baseline Helm chart
- `tests/e2e` cross-service and migration tests
- `reports/blitz` evidence pack structure

## Commands
```bash
make verify-spec
make test-unit
make test-e2e
make test-coverage-core
make up
make smoke-local
make down
```

## Runtime Validation Sequence
```bash
make verify-spec
make test-unit
make test-e2e
make test-coverage-core
make up
make smoke-local
make down
```

## Nightly Containerized E2E
- Workflow: `.github/workflows/nightly-compose-smoke.yml`
- Schedule: daily (`07:15 UTC`) and manual dispatch
- Actions:
  - `make up`
  - `make smoke-local`
  - `make down`
- Artifacts uploaded on every run:
  - `reports/blitz/e2e-results/smoke-local-*.md`
  - `reports/blitz/drill-logs/smoke-local-*.json`
  - `reports/blitz/observability-results/*.json`
  - `reports/blitz/observability-results/*.log`

## Observability (OSS-First)
- Stack components in local compose:
  - OpenTelemetry Collector
  - Prometheus
  - Loki + Promtail
  - Grafana
- Local endpoints:
  - Prometheus: `http://localhost:9090`
  - Grafana: `http://localhost:3000`
  - Loki API: `http://localhost:3100`
  - OTel Collector OTLP gRPC/HTTP: `localhost:4317` / `localhost:4318`
- Reliability metrics exposed for dashboards and alerts:
  - `autotrading_reliability_outbox_backlog_age_ms`
  - `autotrading_reliability_duplicate_suppression_count`
  - `autotrading_reliability_first_status_timeout_count`
- Correlation-aware log format fields:
  - `trace_id`, `request_id`, `idempotency_key`, `principal_id`

## Contributor Instructions
- Full implementation and PR-quality instructions: [docs/IMPLEMENTATION_INSTRUCTIONS.md](docs/IMPLEMENTATION_INSTRUCTIONS.md)

## Slack Agent Status
- Caller workflow: `.github/workflows/agent-status.yml`.
- Uses reusable workflow from `stevelefi/autotrading-devops`.

Required GitHub secrets:
- `SLACK_BOT_TOKEN` (Bot token, `xoxb-...`)
- `SLACK_CHANNEL_ID_STATUS` (target channel ID)

Optional GitHub secret:
- `SLACK_ONCALL_GROUP_ID` (used for `BLOCKED` mentions)
