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

    protected MyEntity() {}                     // JDBC reads

    public MyEntity(String myId, /* ... */) {
        this.myId = myId;
        this.isNewEntity = true;                // INSERT path
    }

    @Override public String getId()  { return myId; }
    @Override public boolean isNew() { return isNewEntity; }
}
```

**Rule 2 — Never map a `String` field to a `jsonb` column.** PostgreSQL rejects `varchar → jsonb`
casts at runtime. Declare JSON blob columns as `TEXT` in Flyway migrations (not `jsonb`).

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
| Primary keys | `TEXT`, format `"prefix-" + UUID.randomUUID()` |
| Timestamps | `TIMESTAMPTZ` always — never `TIMESTAMP WITHOUT TIME ZONE` |
| JSON blobs | `TEXT` — not `jsonb` |
| Enums | `TEXT NOT NULL` — store enum name, not ordinal |
| Tables | Plural `snake_case` — `order_intents`, `risk_decisions` |
| PK columns | `<entity>_id` — `order_intent_id`, `signal_id` |
| Timestamp columns | `<event>_at` — `created_at`, `processed_at` |
| Migrations | **Immutable** once merged — never edit an existing file in `db/migrations/` |
| DDL guards | Always use `IF NOT EXISTS` / `IF EXISTS` on every DDL statement |

New migration files go in `db/migrations/V<max+1>__<short_description>.sql`.

---

## Spring Boot Coding Rules

- **Constructor injection only** — never `@Autowired` on fields.
- **`@Transactional` on service layer only** — never on repositories or controllers.
- **Post-commit Kafka publishes** use `TransactionSynchronizationManager.registerSynchronization()`,
  never fire-and-forget inside a transaction.
- **`MDC.clear()` in `finally`** on every gRPC service method to prevent MDC leakage across thread-pool requests.
- Catch `StatusRuntimeException` at all gRPC client call sites; map to domain error or escalate.
- Publish durable domain events via `KafkaFirstPublisher` (outbox pattern).

---

## Workflow Reminders

1. **Branch name** must pass `python3 scripts/branch_check.py` (GitHub flow: `feature/`, `bugfix/`,
   `hotfix/`, `chore/`, `release/`, or Jira prefix e.g. `AT-1234-`).
2. **Before every commit**: `python3 scripts/check.py --fast` — all checks must be ✅.
3. **Before PR** (for runtime-touching changes): run the full smoke suite:
   ```
   python3 scripts/stack.py up
   python3 scripts/smoke_local.py
   python3 scripts/stack.py down
   ```
   Attach the generated `reports/blitz/e2e-results/smoke-local-<timestamp>.md` path in the PR body.
4. **Helm changes**: always run `helm lint` and `helm template ... > /dev/null` after any change to
   `infra/helm/charts/trading-service`.

---

## Spec Freeze

`SPEC_VERSION.json` pins the spec baseline (currently `spec-v1.0.1-m0m1`).

- No runtime API / Kafka topic / proto / schema changes without a spec-repo PR and new tagged
  baseline first.
- Verify spec sync is current: `python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`.
