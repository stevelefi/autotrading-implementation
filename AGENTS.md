# AGENTS.md

## Required Pre-Implementation Read Path
Before writing code, agents must:
1. Sync pinned spec docs from `SPEC_VERSION.json` into `specs/vendor`.
2. Read `specs/vendor/docs/contracts/**`.
3. Read `specs/vendor/docs/source-of-truth/**`.
4. Read relevant service docs under `specs/vendor/docs/`.
5. Read repo charter index `specs/vendor/docs/repo-charters/README.md` and applicable repo charter `specs/vendor/docs/repo-charters/autotrading-implementation.md`.

## Spec Pinning Guardrail
1. `SPEC_VERSION.json` is authoritative for spec baseline.
2. Implementation work must target the pinned `ref` only.
3. If task requests behavior outside pinned spec, stop and raise spec-change request.

## Blitz Contract Freeze Rule
1. Runtime API/topic/proto/schema changes are frozen during blitz.
2. Blocking defects that require contract change must be raised in spec repo first.
3. Contract change is allowed only after new tagged baseline and spec bump PR.

## PR Workflow Guardrail
1. Branch names must follow: Github flows (e.g. `feature/`, `bugfix/`, `hotfix/`, `chore/`) or be prefixed with a Jira ticket ID (e.g. `AT-1234-`).

## Required Local Checks Before Commit
1. Run `tools/spec_sync.py sync` using `repo_url` and `ref` from `SPEC_VERSION.json`.
2. `python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`.
3. Run unit tests — zero failures tolerated:
   ```
   python3 scripts/test.py unit
   ```
4. Run JaCoCo coverage gate (minimum 50 % line coverage on core modules):
   ```
   python3 scripts/test.py coverage
   ```
5. Run e2e tests:
   ```
   python3 scripts/test.py e2e
   ```
   All five test classes in `tests/e2e/` must pass.
6. Run Helm validation:
   ```
   helm lint infra/helm/charts/trading-service
   helm template trading-service infra/helm/charts/trading-service \
     -f infra/helm/charts/trading-service/values.yaml > /dev/null
   ```

Or run all six checks at once with a single pass/fail summary:
```
python3 scripts/check.py
```
Use `python3 scripts/check.py --fast` to skip e2e during rapid iteration.

## Stack Validation Gate

Mandatory when a change is large or cross-cutting — examples: design changes, new services,
new Kafka topics, DB migrations, Helm / infra / config changes, gRPC or HTTP contract
modifications, multi-module rewrites.  Pure single-module logic or test-only changes may skip this.

### Standard run (preferred)
```
python3 scripts/stack.py up
python3 scripts/smoke_local.py
python3 scripts/stack.py down
```
- `scripts/stack.py up` — brings up infra + all 8 app services in dependency order
- `scripts/smoke_local.py` — runs the 5-phase smoke suite (see table below)
- `scripts/stack.py down` — full teardown including volumes

### Full CI simulation
```
python3 scripts/stack.py ci
```
Runs: down → build → up → validate → teardown.
Use this for a clean slate when the build cache may be stale.

### Partial stack commands
```
python3 scripts/stack.py infra-up      # postgres, redpanda, observability stack only
python3 scripts/stack.py app-up        # app services only (infra must already be up)
python3 scripts/stack.py restart-app   # stop app → rebuild images → start app (infra stays up)
python3 scripts/stack.py status        # show running containers
python3 scripts/stack.py logs          # tail all service logs
```

### Smoke suite — what "PASS" means
`scripts/smoke_local.py` runs 5 phases sequentially; any failure exits non-zero:

| Phase | What it checks |
|-------|---------------|
| 1 — Readiness | All 8 services return `{"status":"UP"}` on `/actuator/health/readiness` (360 s timeout) |
| 2 — Ingress idempotency | Duplicate `idempotency_key` returns 202 with same `ingress_event_id`; conflicting payload returns 409 |
| 3 — Command path | Risk → Order → IBKR; two identical risk calls produce exactly one broker submit (dedup) |
| 4 — Timeout freeze drill | 60 s watchdog triggers `trading_mode=FROZEN`, alert present on `system.alerts.v1` |
| 5 — Async Kafka pipeline | End-to-end ingress POST → broker `total_submit_count` increments within 90 s |

### Evidence artefacts
Smoke writes results to:
- `reports/blitz/e2e-results/smoke-local-<timestamp>.md` — human-readable pass/fail summary
- `reports/blitz/drill-logs/smoke-local-<timestamp>.json` — machine-readable detail

Include the latest smoke report path in any PR acceptance checklist.

## Writing New E2E Tests

All e2e tests live in:
```
tests/e2e/src/test/java/com/autotrading/e2e/
```
Package: `com.autotrading.e2e`. Run with:
```
python3 scripts/test.py e2e
```

### Tooling available
- **JUnit 5** + **AssertJ** + **Mockito** — standard assertions and mocking
- **H2** (in-process) + **Flyway** — schema tests without Docker; mirrors production migrations
- **grpc-inprocess** — in-process gRPC channels for service-layer integration without network

### Existing classes — check before creating a new one

| Class | What it covers |
|-------|---------------|
| `VerticalSliceGrpcFlowTest` | Happy-path ingress → risk → order → ibkr via in-process gRPC |
| `MandatoryScenariosTest` | Spec-mandated scenario checklist (must stay green) |
| `ReliabilityDrillTest` | Outbox/inbox consistency, retry backoff, poller lifecycle |
| `DataConsistencyTest` | Cross-service DB state coherence after a full command chain |
| `FlywayMigrationTest` | Schema assertions: migration count, required column presence |

### When to extend vs. create
- **Extend** an existing class when the scenario is closely related to what it already covers.
- **Create** a new `<Feature>Test.java` when adding a new Kafka consumer path, a new gRPC
  endpoint, or a new failure / fallback mode that has no existing coverage home.

## Scripts Quick Reference

| Script | Purpose | Key commands |
|--------|---------|-------------|
| `scripts/test.py` | Maven test runner | `unit` \| `coverage` \| `e2e` \| `all` — add `--module <path>` to target one module |
| `scripts/check.py` | Pre-commit gate (all checks + summary) | *(no args)* full gate; `--fast` skips e2e; `--skip-helm` skips Helm; `--only <check>...` |
| `scripts/stack.py` | Local stack manager | `up` \| `down` \| `infra-up` \| `app-up` \| `app-down` \| `restart-app` \| `build` \| `status` \| `logs` \| `validate` \| `ci` |
| `scripts/smoke_local.py` | 5-phase smoke suite (requires stack up) | *(no args)* — writes reports to `reports/blitz/` |
| `scripts/trace.py` | Query Loki logs by MDC field | `--trace-id` \| `--idempotency-key` \| `--agent-id` \| `--order-intent-id` \| `--signal-id` \| `--service` \| `--since` |

## Change Control
1. Any contract change starts in spec repo, not here.
2. After spec tag bump, run sync and open dedicated "spec bump" PR.
