# GitHub Copilot — Workspace Instructions

This file provides project-specific context for GitHub Copilot completions and chat in the
`autotrading-implementation` repository. Keep it up to date with `AGENTS.md`.

---

## Stack Snapshot

| Item | Value |
|------|-------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Persistence | Spring Data JDBC (**not JPA**) + PostgreSQL |
| Migrations | Flyway (`db/migrations/`) |
| Messaging | Kafka (Redpanda locally) |
| RPC | gRPC 1.66.0 + Protobuf 3.25.3 |
| Reliability | Outbox/inbox pattern (`libs/reliability-core`) |
| Build | Maven multi-module |

---

## Required Pre-Implementation Read Path

Before writing code:
1. Sync pinned spec docs from `SPEC_VERSION.json` into `specs/vendor`.
2. Read `specs/vendor/docs/contracts/**`.
3. Read `specs/vendor/docs/source-of-truth/**`.
4. Read relevant service docs under `specs/vendor/docs/`.
5. Read repo charter index `specs/vendor/docs/repo-charters/README.md` and applicable repo charter
   `specs/vendor/docs/repo-charters/autotrading-implementation.md`.

## Spec Pinning Guardrail

- `SPEC_VERSION.json` is authoritative for the spec baseline.
- Implementation work must target the pinned `ref` only.
- If a task requests behavior outside the pinned spec, stop and raise a spec-change request.

## Blitz Contract Freeze Rule

- Runtime API / topic / proto / schema changes are frozen during blitz.
- Blocking defects that require a contract change must be raised in the spec repo first.
- A contract change is allowed only after a new tagged baseline and spec bump PR.

---

## Required Local Checks Before Commit

1. Sync and verify spec:
   ```
   python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
   ```
2. Unit tests — zero failures tolerated:
   ```
   python3 scripts/test.py unit
   ```
3. JaCoCo coverage gate (minimum 50 % line coverage on core modules):
   ```
   python3 scripts/test.py coverage
   ```
4. E2E tests — all five test classes in `tests/e2e/` must pass:
   ```
   python3 scripts/test.py e2e
   ```
5. Helm validation:
   ```
   helm lint infra/helm/charts/trading-service
   helm template trading-service infra/helm/charts/trading-service \
     -f infra/helm/charts/trading-service/values.yaml > /dev/null
   ```

Or run all checks at once:
```
python3 scripts/check.py
```
Use `python3 scripts/check.py --fast` to skip e2e during rapid iteration.

---

## Stack Validation Gate

Mandatory for large or cross-cutting changes (new services, Kafka topics, DB migrations, Helm /
infra / config changes, gRPC or HTTP contract modifications, multi-module rewrites). Pure
single-module logic or test-only changes may skip this.

### Standard run (preferred)
```
python3 scripts/stack.py up
python3 scripts/smoke_local.py
python3 scripts/stack.py down
```

### Full CI simulation
```
python3 scripts/stack.py ci
```
Runs: down → build → up → validate → teardown. Use when the build cache may be stale.

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
| 2 — Ingress idempotency | Duplicate `client_event_id` returns 202 with same `event_id` (first-write-wins); conflicting payload on same key also returns 202 replaying the original |
| 3 — Command path | Risk → Order → IBKR; two identical risk calls produce exactly one broker submit (dedup) |
| 4 — Timeout freeze drill | 60 s watchdog triggers `trading_mode=FROZEN`, alert present on `system.alerts.v1` |
| 5 — Async Kafka pipeline | End-to-end ingress POST → broker `total_submit_count` increments within 90 s |

Smoke writes results to:
- `reports/blitz/e2e-results/smoke-local-<timestamp>.md` — human-readable pass/fail summary
- `reports/blitz/drill-logs/smoke-local-<timestamp>.json` — machine-readable detail

Include the latest smoke report path in any PR acceptance checklist.

---

## Development Workflow — Fast Iteration

During active development, **keep infrastructure running** and only rebuild/redeploy app services.
This avoids the ~2 min Flyway/Redpanda init cost on every iteration.

```
# One-time infra bring-up per session
python3 scripts/stack.py infra-up

# After every code change
python3 scripts/stack.py restart-app

# End of session
python3 scripts/stack.py down
```

| Command | What it touches | When to use |
|---------|----------------|-------------|
| `infra-up` | postgres, redpanda, observability | once per dev session |
| `app-up` | 8 application microservices | after `infra-up` or `build` |
| `restart-app` | app only (stop → build → start) | after every code change |
| `app-down` | app only (infra stays) | pause app without losing DB/Kafka state |
| `up` | full stack | smoke tests / CI |
| `down` | full stack + volumes | clean slate / end of session |

---

## Scripts Quick Reference

| Script | Purpose | Key commands |
|--------|---------|-------------|
| `scripts/branch_check.py` | GitHub flow branch name validator | *(no args)* checks current branch; `<name>` checks a specific name |
| `scripts/onboard.py` | Account / agent / API-key / broker-account management CLI | `account create/list`, `agent create/list`, `apikey generate/create/list/revoke`, `broker create/list` |
| `scripts/pr.py` | Safe branch + commit + push + GitHub PR (no shell-quoting issues) | `--branch NAME --title TEXT [--body TEXT] [--draft] [--commit-only] [--push-only]` |
| `scripts/test.py` | Maven test runner | `unit` \| `coverage` \| `e2e` \| `all` — add `--module <path>` to target one module |
| `scripts/check.py` | Pre-commit gate (all checks + summary) | *(no args)* full gate; `--fast` skips e2e; `--skip-helm` skips Helm; `--only <check>...` |
| `scripts/stack.py` | Local stack manager | `up` \| `down` \| `infra-up` \| `app-up` \| `app-down` \| `restart-app` \| `build` \| `status` \| `logs` \| `validate` \| `ci` |
| `scripts/smoke_local.py` | 5-phase smoke suite (requires stack up) | *(no args)* — writes reports to `reports/blitz/` |
| `scripts/trace.py` | Query Loki logs by MDC field | `--trace-id` \| `--client-event-id` \| `--agent-id` \| `--order-intent-id` \| `--signal-id` \| `--service` \| `--since` |

---

## DevOps Workflow

### Rule 1 — Always Start With a Branch
```
git checkout main && git pull
git checkout -b feature/<short-topic>
python3 scripts/branch_check.py    # must print OK
```
Never commit directly to `main`. Branch names must satisfy `scripts/branch_check.py`.

### Rule 2 — Full CI Gate Before Every Commit
```
python3 scripts/check.py --fast    # skip e2e during rapid iteration
python3 scripts/check.py           # full gate before final commit
```
All checks must be ✅. A single ❌ blocks the commit.

### Rule 3 — Smoke Before PR
```
python3 scripts/stack.py up
python3 scripts/smoke_local.py     # must exit 0
python3 scripts/stack.py down
```
Attach the generated `reports/blitz/e2e-results/smoke-local-<timestamp>.md` path in the PR body.
For large/cross-cutting changes, also run `python3 scripts/stack.py ci`.

### Rule 4 — Atomic, Descriptive Commits
- One logical concern per commit.
- Subject line: `<type>(<scope>): <imperative summary>` — e.g. `fix(risk): correct Persistable isNew flag`
- Types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`, `perf`
- Use `python3 scripts/pr.py` to avoid shell-quoting issues when committing and creating PRs.

### Rule 5 — Pre-Push Checklist
- [ ] `python3 scripts/branch_check.py` prints OK
- [ ] `python3 scripts/check.py` (or `--fast` for iteration) — all ✅
- [ ] `python3 scripts/smoke_local.py` exits 0 (for runtime-touching changes)
- [ ] No secrets, no `target/` outputs, no `.env` files staged
- [ ] PR body includes smoke report path, linked issue, pinned spec ref, acceptance checklist

### Rule 6 — Helm Hygiene
- Never hard-code image tags in `values.yaml` — use `image.tag` parameter driven by CI.
- Run after every Helm change:
  ```
  helm lint infra/helm/charts/trading-service
  helm template trading-service infra/helm/charts/trading-service \
    -f infra/helm/charts/trading-service/values.yaml > /dev/null
  ```

### Rule 7 — Migration Safety
After writing a Flyway migration, always do a full stack cycle before committing:
```
python3 scripts/stack.py down
python3 scripts/stack.py infra-up
docker logs flyway-init-1 2>&1 | tail -20
python3 scripts/smoke_local.py
```
Include `docker logs flyway-init-1` output snippet as evidence in the PR.

---

## Spring Data JDBC — 5 Critical Rules

**Rule 1 — Every entity with a String PK must implement `Persistable<String>`** with a
`@Transient boolean isNewEntity` flag set to `true` in the public constructor and `false` by the
no-arg constructor used by JDBC reads. Without this, Spring Data JDBC issues `UPDATE` instead of
`INSERT`, throwing `IncorrectUpdateSemanticsDataAccessException`.

```java
@Table("my_table")
public class MyEntity implements Persistable<String> {
    @Id @Column("my_id") private String myId;
    @Transient private boolean isNewEntity;

    protected MyEntity() {}                     // JDBC reads — isNewEntity stays false

    public MyEntity(String myId, /* ... */) {
        this.myId = myId;
        this.isNewEntity = true;                // INSERT path
    }

    @Override public String getId()  { return myId; }
    @Override public boolean isNew() { return isNewEntity; }
}
```

**Rule 2 — Never map a `String` field to a `jsonb` column.** PostgreSQL rejects `varchar → jsonb`
casts at runtime. Declare JSON blob columns as `TEXT` in Flyway migrations (not `jsonb`). Existing
jsonb columns were converted in `db/migrations/V7__jsonb_columns_to_text.sql`.

**Rule 3 — Always annotate with `@Table` and `@Column`.** Never rely on Spring Data naming
conventions. Use `@Table("exact_table_name")` on the class and `@Column("exact_column_name")` on
every field.

**Rule 4 — Upserts require `NamedParameterJdbcTemplate`.** `save()` cannot express
`ON CONFLICT DO UPDATE`. Use `NamedParameterJdbcTemplate` with explicit upsert SQL:
```sql
INSERT INTO my_table (id, col1) VALUES (:id, :col1)
ON CONFLICT (id) DO UPDATE SET col1 = EXCLUDED.col1;
```

**Rule 5 — Use `:named` parameters only.** Never use positional `?`. Use `MapSqlParameterSource`
or `BeanPropertySqlParameterSource` with all `NamedParameterJdbcTemplate` calls.

---

## Database Conventions

| Convention | Rule |
|-----------|-----|
| Primary keys | `TEXT` — format `"prefix-" + UUID.randomUUID()` |
| Timestamps | `TIMESTAMPTZ` always — never `TIMESTAMP WITHOUT TIME ZONE` |
| JSON blobs | `TEXT` — not `jsonb`; mapped as `String` in Java |
| Short strings | `TEXT` — avoid `VARCHAR(n)` unless there is a hard domain constraint |
| Booleans | `BOOLEAN NOT NULL DEFAULT FALSE` |
| Enums | `TEXT NOT NULL` — store enum name, not ordinal |
| Foreign keys | Define with `REFERENCES` but **no `ON DELETE CASCADE`** by default |
| Tables | Plural `snake_case` — `order_intents`, `risk_decisions` |
| PK columns | `<entity>_id` — `order_intent_id`, `signal_id` |
| Timestamp columns | `<event>_at` — `created_at`, `processed_at`, `submitted_at` |
| JSON blob columns | `<field>_json` — `payload_json`, `deny_reasons_json` |
| Indexes | `idx_<table>_<col1>[_<col2>]` — `idx_order_intents_agent_id` |
| Migrations | **Immutable** once merged — never edit an existing file in `db/migrations/` |
| DDL guards | Always use `IF NOT EXISTS` / `IF EXISTS` on every DDL statement |

New migration files go in `db/migrations/V<max+1>__<short_description>.sql`.
Start every file with a comment: `-- V<n>: <one-line reason for this migration>`.

### Production Safety Checklist (before merging any migration)
- [ ] All DDL uses `IF NOT EXISTS` / `IF EXISTS` guards
- [ ] No `DROP TABLE` or `DROP COLUMN` without a two-phase rollout plan
- [ ] No migration edits an existing file in `db/migrations/`
- [ ] Flyway `docker logs flyway-init-1` shows clean apply after `stack.py down && infra-up`
- [ ] No data migration touches more than ~1 000 rows without a backfill strategy
- [ ] `python3 scripts/smoke_local.py` exits 0 after the migration

---

## Spring Boot Coding Rules

- **Constructor injection only** — never `@Autowired` on fields.
- **Declare all beans in `@Configuration` classes** — avoid `@Component` on business objects.
- **`@Transactional` on service layer only** — never on repositories or controllers.
- **Group config properties** under `@ConfigurationProperties`-bound classes.
- **Post-commit Kafka publishes** use `TransactionSynchronizationManager.registerSynchronization()`,
  never fire-and-forget inside a transaction.
- **`MDC.clear()` in `finally`** on every gRPC service method to prevent MDC leakage across thread-pool requests.
- Catch `StatusRuntimeException` at all gRPC client call sites; map to domain error or escalate.
- Publish durable domain events via `KafkaFirstPublisher` (outbox pattern).

## Java 21 Rules

- Use records for DTOs and value objects; prefer switch expressions over if-else chains.
- Never swallow exceptions with empty `catch` blocks — at minimum log with MDC context.
- Declare thread-safety intent explicitly: `volatile`, `CopyOnWriteArrayList`, `ConcurrentHashMap.compute()`.

---

## Writing New E2E Tests

All e2e tests live in `tests/e2e/src/test/java/com/autotrading/e2e/`. Run with:
```
python3 scripts/test.py e2e
```

### Existing classes — check before creating a new one

| Class | What it covers |
|-------|---------------|
| `VerticalSliceGrpcFlowTest` | Happy-path ingress → risk → order → ibkr via in-process gRPC |
| `MandatoryScenariosTest` | Spec-mandated scenario checklist (must stay green) |
| `ReliabilityDrillTest` | Outbox/inbox consistency, retry backoff, poller lifecycle |
| `DataConsistencyTest` | Cross-service DB state coherence after a full command chain |
| `FlywayMigrationTest` | Schema assertions: migration count, required column presence |

- **Extend** an existing class when the scenario is closely related to what it already covers.
- **Create** a new `<Feature>Test.java` for a new Kafka consumer path, gRPC endpoint, or failure mode with no existing coverage home.

---

## Spec Freeze

`SPEC_VERSION.json` pins the spec baseline (currently `spec-v1.0.1-m0m1`).

- No runtime API / Kafka topic / proto / schema changes without a spec-repo PR and new tagged
  baseline first.
- Verify spec sync is current: `python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`.
- Any contract change starts in the spec repo, not here. After a spec tag bump, run sync and open
  a dedicated "spec bump" PR.
