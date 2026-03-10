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
1. Branch names must follow: Github flows (e.g. `feature/`, `bugfix/`, `hotfix/`, `chore/`, `release/`) or be prefixed with a Jira ticket ID (e.g. `AT-1234-`).
2. Validate before pushing — the pre-commit gate (`scripts/check.py`) runs this automatically as check 1/7:
   ```
   python3 scripts/branch_check.py
   ```

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
- `scripts/smoke_local.py` — runs the 6-phase smoke suite (see table below)
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
`scripts/smoke_local.py` runs 6 phases sequentially; any failure exits non-zero:

| Phase | What it checks |
|-------|---------------|
| 1 — Readiness | All 8 services return `{"status":"UP"}` on `/actuator/health/readiness` (360 s timeout) |
| 2 — Ingress idempotency | Duplicate `client_event_id` returns 202 with same `event_id` (first-write-wins); conflicting payload on same key also returns 202 replaying the original |
| 3 — Command path | Risk → Order → IBKR; two identical risk calls produce exactly one broker submit (dedup) |
| 4 — Timeout freeze drill | 60 s watchdog triggers `trading_mode=FROZEN`, alert present on `system.alerts.v1` |
| 5 — Async Kafka pipeline | End-to-end ingress POST → broker `total_submit_count` increments within 90 s |
| 6 — Auth edge cases | Missing header → 400; non-Bearer scheme → 400; unknown key → 401; cross-account agent → 403; valid key + owned agent → 202 |

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

## Development Workflow — Fast Iteration

During active development, **keep infrastructure running** and only rebuild/redeploy the
application services. This avoids the ~2 min Flyway/Redpanda init cost on every iteration.

### One-time infra bring-up
```
python3 scripts/stack.py infra-up
```
Starts: postgres, flyway-init, redpanda, redpanda-init, redpanda-console, otel-collector,
prometheus, loki, promtail, grafana, ibkr-simulator.

Leave this running for the duration of your development session.

### Fast redeploy loop (after every code change)
```
# Rebuild images + redeploy all 8 app services (infra unchanged):
python3 scripts/stack.py restart-app

# Or step by step:
python3 scripts/stack.py build        # rebuild app Docker images
python3 scripts/stack.py app-up       # (re)start app services
```

### Useful during iteration
```
python3 scripts/stack.py status       # show running containers
python3 scripts/stack.py logs         # tail all service logs
python3 scripts/stack.py logs --service ingress-gateway-service   # single service
```

### End of session / CI
```
python3 scripts/stack.py down         # full teardown including volumes
```

### Summary of infra vs. app split

| Command | What it touches | When to use |
|---------|----------------|-------------|
| `infra-up` | postgres, redpanda, observability | once per dev session |
| `app-up` | 8 application microservices | after `infra-up` or `build` |
| `restart-app` | app only (stop → build → start) | after every code change |
| `app-down` | app only (infra stays) | pause app without losing DB/Kafka state |
| `up` | full stack | smoke tests / CI |
| `down` | full stack + volumes | clean slate / end of session |

## Scripts Quick Reference

| Script | Purpose | Key commands |
|--------|---------|-------------|
| `scripts/branch_check.py` | GitHub flow branch name validator | *(no args)* checks current branch; `<name>` checks a specific name |
| `scripts/onboard.py` | Account / agent / API-key / broker-account management CLI | `account create/list`, `agent create/list`, `apikey generate/create/list/revoke`, `broker create/list` |
| `scripts/test.py` | Master test runner | `unit` \| `coverage` \| `e2e` \| `smoke` \| `load` \| `manual` \| `all` \| `full` — add `--module <path>` to target one module; `full` = unit+coverage+e2e+smoke |
| `scripts/check.py` | Pre-commit gate (all checks + summary) | *(no args)* full gate; `--fast` skips e2e; `--skip-helm` skips Helm; `--only <check>...` |
| `scripts/stack.py` | Local stack manager | `up` \| `down` \| `infra-up` \| `app-up` \| `app-down` \| `restart-app` \| `build` \| `status` \| `logs` \| `validate` \| `ci` |
| `scripts/smoke_local.py` | 6-phase smoke suite (requires stack up) | *(no args)* — writes reports to `reports/blitz/` |
| `scripts/trace.py` | Query Loki logs by MDC field | `--trace-id` \| `--client-event-id` \| `--agent-id` \| `--order-intent-id` \| `--signal-id` \| `--service` \| `--since` |

## Change Control
1. Any contract change starts in spec repo, not here.
2. After spec tag bump, run sync and open dedicated "spec bump" PR.

---

## Skill: Java / Spring Boot / Spring Data JDBC

### Java 21
- Use records for DTOs and value objects; prefer switch expressions over if-else chains.
- Never swallow exceptions with empty `catch` blocks — at minimum log with MDC context.
- Declare thread-safety intent explicitly: `volatile`, `CopyOnWriteArrayList`, `ConcurrentHashMap.compute()`.

### Spring Boot 3
- Constructor injection only — never `@Autowired` on fields.
- Declare all beans in `@Configuration` classes; avoid `@Component` on business objects.
- `@Transactional` boundaries belong on the **service layer**, never on repository or controller.
- Post-commit side effects (e.g. Kafka publishes) use `TransactionSynchronizationManager.registerSynchronization()`.
- Group related config properties under `@ConfigurationProperties`-bound classes.

### Spring Data JDBC (not JPA)
This codebase uses Spring Data JDBC throughout. Rules:

**Rule 1 — `Persistable<String>` is mandatory on every entity with a String PK.**
Every entity must implement `Persistable<String>` with a `@Transient boolean isNewEntity` flag:
```java
@Table("my_table")
public class MyEntity implements Persistable<String> {
    @Id @Column("my_id") private String myId;
    // ... other @Column fields ...
    @Transient private boolean isNewEntity;

    protected MyEntity() {}                 // JDBC reads — isNewEntity stays false

    public MyEntity(String myId, ...) {
        this.myId = myId;
        // ... assign fields ...
        this.isNewEntity = true;            // marks this instance as INSERT
    }

    @Override public String getId()  { return myId; }
    @Override public boolean isNew() { return isNewEntity; }
}
```
Without this, Spring Data JDBC issues `UPDATE` instead of `INSERT` when the ID is already set,
throwing `IncorrectUpdateSemanticsDataAccessException`.

**Rule 2 — Never map a `String` field to a `jsonb` column.**
PostgreSQL rejects `varchar → jsonb` casts at runtime (`PSQLException: column is of type jsonb`).
Declare JSON blob columns as `text` in Flyway migrations. Existing jsonb columns were converted
in `db/migrations/V7__jsonb_columns_to_text.sql`.

**Rule 3 — Always use `@Table` and `@Column`.**
Do not rely on Spring Data naming conventions. Every entity class must have `@Table("exact_table_name")`
and every field must have `@Column("exact_column_name")`.

**Rule 4 — ON CONFLICT / upsert requires `NamedParameterJdbcTemplate`.**
Spring Data JDBC `save()` cannot express `ON CONFLICT DO UPDATE`. Use `NamedParameterJdbcTemplate`
with a `:named` parameter SQL string for upsert patterns.

**Rule 5 — Use `:named` parameters, never positional `?`.**
All `NamedParameterJdbcTemplate` SQL must use `:paramName` placeholders and `MapSqlParameterSource`
or `BeanPropertySqlParameterSource`.

### Error Handling
- Durable domain events must be published via `KafkaFirstPublisher` (outbox pattern) — never fire-and-forget.
- Catch `StatusRuntimeException` at all gRPC client call sites; map to domain error or escalate.
- `MDC.clear()` in `finally` blocks on every gRPC service method to prevent MDC key leakage across thread-pool requests.

---

## Skill: PostgreSQL Database Design & Production Schema Management

### Schema Design Conventions
This codebase establishes the following conventions (see `db/migrations/V1__baseline.sql`):

| Convention | Rule |
|-----------|-----|
| Primary keys | `TEXT` — format `"prefix-" + UUID.randomUUID()` |
| Timestamps | `TIMESTAMPTZ` always — never `TIMESTAMP WITHOUT TIME ZONE` |
| JSON blobs | `TEXT` — not `jsonb`; mapped as `String` in Java (see Rule 2 above) |
| Short strings | `TEXT` — avoid `VARCHAR(n)` unless there is a hard domain constraint |
| Booleans | `BOOLEAN NOT NULL DEFAULT FALSE` |
| Enums | `TEXT NOT NULL` — store the enum name string, not ordinal |
| Foreign keys | Define with `REFERENCES` but **no `ON DELETE CASCADE`** by default |
| Composite PKs | Use for junction/sequence tables |

**Naming conventions:**
- Tables: plural `snake_case` — `order_intents`, `risk_decisions`
- Primary key columns: `<entity>_id` — `order_intent_id`, `signal_id`
- Timestamp columns: `<event>_at` — `created_at`, `processed_at`, `submitted_at`
- JSON blob columns: `<field>_json` — `payload_json`, `deny_reasons_json`
- Indexes: `idx_<table>_<col1>[_<col2>]` — `idx_order_intents_agent_id`

### Flyway Migration Discipline
1. Place new files in `db/migrations/` as `V<max+1>__<short_description>.sql`.
2. Start every file with a comment: `-- V<n>: <one-line reason for this migration>`.
3. Migrations are **immutable** once merged to `main` — never edit an existing file.
4. Use `IF NOT EXISTS` / `IF EXISTS` guards on every DDL statement:
   ```sql
   ALTER TABLE my_table ADD COLUMN IF NOT EXISTS new_col TEXT;
   CREATE INDEX IF NOT EXISTS idx_my_table_col ON my_table(col);
   ```
5. After writing a new migration, verify with a full stack cycle:
   ```
   python3 scripts/stack.py down
   python3 scripts/stack.py infra-up
   docker logs flyway-init-1 2>&1 | tail -20   # must end with "Successfully applied N migrations"
   python3 scripts/smoke_local.py
   ```
6. Never use `db/migrations/` for data backfills on large tables without an explicit migration plan.

### SQL Standards
- Use CTEs (`WITH ...`) for multi-step queries — avoid deeply nested subqueries.
- Always list columns explicitly in `INSERT` — never `INSERT INTO t VALUES (...)`.
- Upsert pattern:
  ```sql
  INSERT INTO my_table (id, col1, col2)
  VALUES (:id, :col1, :col2)
  ON CONFLICT (id) DO UPDATE SET col1 = EXCLUDED.col1, col2 = EXCLUDED.col2;
  ```
- Run `EXPLAIN ANALYZE` before merging any query operating on tables with >10k rows.
- Never `SELECT *` — name the columns needed.
- Prefer set-based operations over row-by-row cursor/loop patterns.

### Indexing Rules
1. Every foreign key column must have an index.
2. Every column used in `WHERE`, `ORDER BY`, or `JOIN ON` on a hot path must be indexed.
3. Composite index column order: equality predicates first, range predicates last.
4. Partial indexes for filtered queries:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
     ON outbox_events(created_at) WHERE published = FALSE;
   ```
5. For live tables: `CREATE INDEX CONCURRENTLY` — but note Flyway runs in a transaction;
   place CONCURRENTLY indexes in a separate migration file with `spring.flyway.mixed=true` or
   outside Flyway and document explicitly.

### Stored Procedures / Functions
- Use sparingly — business logic belongs in Java services.
- When a function is the right tool (e.g. atomic multi-row update with conditional logic):
  - Name: `CREATE OR REPLACE FUNCTION fn_<verb>_<noun>(...)` — e.g. `fn_claim_outbox_batch`
  - Test via the service layer, not ad-hoc SQL
  - Place in a dedicated Flyway migration file (not mixed with DDL)
- Never drop or replace a function that is called by live application code without a coordinated deploy.

### Destructive Migrations — Two-Phase Rollout
To safely drop a column or table:
- **Phase 1** (this PR): make application code tolerate both old and new schema (stop reading/writing the column).
- **Phase 2** (follow-up PR, after Phase 1 is deployed and verified): apply `ALTER TABLE … DROP COLUMN IF EXISTS`.

### Production Safety Checklist
Before merging any migration:
- [ ] All DDL uses `IF NOT EXISTS` / `IF EXISTS` guards
- [ ] No `DROP TABLE` or `DROP COLUMN` without a two-phase plan
- [ ] No migration edits an existing file in `db/migrations/`
- [ ] Flyway `docker logs flyway-init-1` shows clean apply after `stack.py down && infra-up`
- [ ] No data migration touches more than ~1 000 rows without a backfill strategy
- [ ] `python3 scripts/smoke_local.py` exits 0 after the migration
- [ ] PR includes smoke report path as evidence

---

## Skill: DevOps Workflow

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

For any large or cross-cutting change (new service, DB migration, Helm change, contract modification),
also run the full CI simulation:
```
python3 scripts/stack.py ci
```

### Rule 4 — Atomic, Descriptive Commits
- One logical concern per commit.
- Subject line format: `<type>(<scope>): <imperative summary>` — e.g. `fix(risk): correct Persistable isNew flag`
- Types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`, `perf`
- Before committing: `git diff --staged` — verify only intended changes are staged.

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
- Do not bleed local docker-compose config into Helm charts.

### Rule 7 — Migration Safety
After writing or editing a Flyway migration file, always do a full stack cycle before committing:
```
python3 scripts/stack.py down
python3 scripts/stack.py infra-up
docker logs flyway-init-1 2>&1 | tail -20
python3 scripts/smoke_local.py
```
Include `docker logs flyway-init-1` output snippet as evidence in the PR if the change includes a migration.
