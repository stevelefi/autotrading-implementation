# Implementation Instructions

## Purpose
This document defines the production-standard workflow for implementing and shipping changes in this repository.

## 1. Baseline and Contract Discipline
1. `SPEC_VERSION.json` is authoritative for spec baseline.
2. Before coding, run spec sync + verify:
   - `python3 tools/spec_sync.py sync --repo-url <repo_url> --ref <ref> --dest specs/vendor --version-file SPEC_VERSION.json`
   - `python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`
3. Read required docs:
   - `specs/vendor/docs/contracts/**`
   - `specs/vendor/docs/source-of-truth/**`
4. Do not introduce runtime contract changes during blitz (API/topic/proto/schema freeze).

## 2. Branch and PR Standard
1. Branch naming must follow: `codex/<repo>-<issue>-<short-topic>`.
2. PR must include:
   - linked issue (`Closes #<id>` / `Fixes #<id>` / `Resolves #<id>`)
   - pinned spec ref
   - acceptance checklist
   - evidence links (tests, drill logs, runbook updates)

## 3. .gitignore and Repository Hygiene
1. Do not commit generated build outputs (`**/target/`, logs, temp files).
2. Do not commit secrets (`.env`, `.env.local`, credential files).
3. Commit templates only (`.env.example`, `infra/local/.env.compose.example`).
4. Keep local spec sync artifacts untracked (`specs/vendor/` is intentionally ignored).

## 4. Required Local Validation Before Commit
1. `python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`
2. `mvn -B test`
3. `make test-coverage-core` (core line coverage gate for reliability path modules)
4. `helm lint infra/helm/charts/trading-service`
5. `helm template trading-service infra/helm/charts/trading-service -f infra/helm/charts/trading-service/values.yaml`
6. Optional local config check:
   - `docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml config`
7. Runtime smoke sequence (required for runtime-touching changes):
   - `make up`
   - `make smoke-local`
   - `make down`

## 4.1 Nightly Containerized E2E
1. Workflow: `.github/workflows/nightly-compose-smoke.yml`
2. Runs daily and on manual dispatch.
3. Must execute:
   - `make up`
   - `make smoke-local`
   - `make down`
4. Upload smoke evidence artifacts from:
   - `reports/blitz/e2e-results/smoke-local-*.md`
   - `reports/blitz/drill-logs/smoke-local-*.json`
   - `reports/blitz/observability-results/*.json`
   - `reports/blitz/observability-results/*.log`

## 4.2 Observability Dev Flow (OSS-First)
1. Local stack runs via `infra/local/docker-compose.yml` with:
   - `otel-collector`
   - `prometheus`
   - `loki`
   - `promtail`
   - `grafana`
2. Service-level requirements:
   - expose `prometheus` actuator endpoint
   - emit structured logs containing `trace_id`, `request_id`, `idempotency_key`, `principal_id`
   - propagate correlation context for inbound HTTP and gRPC
3. Reliability telemetry expected in dashboards and alerts:
   - `autotrading_reliability_outbox_backlog_age_ms`
   - `autotrading_reliability_duplicate_suppression_count`
   - `autotrading_reliability_first_status_timeout_count`
4. Vendor handoff model:
   - keep OpenTelemetry collector as control plane
   - add Datadog/Splunk exporters in collector config without app rewrites

## 5. Reliability and Safety Expectations
1. Idempotency behavior:
   - same key + same payload => replay existing acceptance
   - same key + different payload => conflict
2. Outbox/inbox behavior:
   - outbox appended in same transaction as domain mutation
   - duplicate consumer event cannot create duplicate side effects
3. Command safety behavior:
   - retry with same idempotency key cannot create duplicate broker submit
   - missing first status within 60 seconds triggers `UNKNOWN_PENDING_RECON` and `FROZEN`
4. Observability minimum:
   - outbox backlog age
   - duplicate suppression count
   - first-status timeout count

## 6. Evidence Pack Update
When implementation changes behavior, update:
- `reports/blitz/day*.md`
- `reports/blitz/known-risks.md`
- relevant runbook entries in `docs/runbooks/`
- include generated smoke report paths from:
  - `reports/blitz/e2e-results/smoke-local-*.md`
  - `reports/blitz/drill-logs/smoke-local-*.json`
  - `reports/blitz/observability-results/*.json`
  - `reports/blitz/observability-results/*.log`
