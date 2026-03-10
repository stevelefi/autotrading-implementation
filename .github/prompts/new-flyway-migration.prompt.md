# Adding a New Flyway Migration

Use this prompt when you need to add a new database migration to `db/migrations/`.

---

## Rules (from AGENTS.md)

1. File must be `V<max+1>__<short_description>.sql` — find the current max version:
   ```bash
   ls db/migrations/ | sort
   ```
2. Start with a comment block:
   ```sql
   -- V<n>: <one-line reason for this migration>
   ```
3. **All DDL must use `IF NOT EXISTS` / `IF EXISTS` guards** — migrations are immutable once merged.
4. Migrations live forever — **never edit an existing file** in `db/migrations/`.
5. After writing, validate with a full stack cycle (see below).

---

## Schema Conventions

| Concern | Rule |
|---------|------|
| Primary keys | `TEXT NOT NULL PRIMARY KEY` — format `"prefix-" + UUID.randomUUID()` |
| Timestamps | `TIMESTAMPTZ NOT NULL DEFAULT now()` — never `TIMESTAMP WITHOUT TIME ZONE` |
| JSON blobs | `TEXT` — not `jsonb` (Spring Data JDBC Rule 2) |
| Short strings | `TEXT` — avoid `VARCHAR(n)` without a hard domain constraint |
| Booleans | `BOOLEAN NOT NULL DEFAULT FALSE` |
| Enums stored in DB | `TEXT NOT NULL` — store enum `.name()`, not ordinal |
| Foreign keys | `REFERENCES <table> (<col>)` — no `ON DELETE CASCADE` by default |
| Table names | Plural `snake_case` |
| PK column names | `<entity>_id` — e.g. `order_intent_id` |
| Timestamp columns | `<event>_at` — e.g. `created_at`, `submitted_at` |
| JSON columns | `<field>_json` — e.g. `payload_json` |
| Index names | `idx_<table>_<col1>[_<col2>]` |

---

## Migration Template

```sql
-- V<n>: <one-line description>

CREATE TABLE IF NOT EXISTS my_new_records (
    my_record_id   TEXT        NOT NULL PRIMARY KEY,
    account_id     TEXT        NOT NULL REFERENCES accounts (account_id),
    payload_json   TEXT        NOT NULL,
    status         TEXT        NOT NULL DEFAULT 'PENDING',
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_my_new_records_account_id ON my_new_records (account_id);
CREATE INDEX IF NOT EXISTS idx_my_new_records_status     ON my_new_records (status)
    WHERE status != 'DONE';   -- partial index for hot queries

-- Upsert seed (idempotent dev data only)
INSERT INTO my_new_records (my_record_id, account_id, payload_json, status, created_at, updated_at)
VALUES ('mnr-seed-001', 'acc-local-dev', '{}', 'PENDING', now(), now())
ON CONFLICT (my_record_id) DO NOTHING;
```

---

## Adding a Column to an Existing Table

```sql
-- V<n>: add retry_count to existing_records

ALTER TABLE existing_records
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_error  TEXT;

-- Add index if the column will be used in WHERE
CREATE INDEX IF NOT EXISTS idx_existing_records_retry_count
    ON existing_records (retry_count)
    WHERE retry_count > 0;
```

---

## Dev Seed Pattern

Any seed rows must use `ON CONFLICT … DO NOTHING` so re-running the migration is safe:

```sql
INSERT INTO accounts (account_id, display_name, active, created_at)
VALUES ('acc-my-seed', 'Seed Account', TRUE, now())
ON CONFLICT (account_id) DO NOTHING;
```

For SHA-256 API key seeds, compute the hash in Python:
```python
import hashlib
raw = "my-dev-key"
print(hashlib.sha256(raw.encode()).hexdigest())
```

---

## Validation Steps (Required Before Commit)

```bash
# 1. Full teardown + re-init (simulates first-time apply)
python3 scripts/stack.py down
python3 scripts/stack.py infra-up

# 2. Check Flyway applied cleanly
docker logs flyway-init-1 2>&1 | tail -20
# Must end with: "Successfully applied N migrations to schema "public""

# 3. Run smoke to verify no regressions
python3 scripts/smoke_local.py

# 4. Add to FlywayMigrationTest.java assertion (count check)
# In tests/e2e/ → FlywayMigrationTest → update expected migration count
```

---

## Destructive Changes — Two-Phase Rollout

**Never drop a column or table in a single PR.**

- **Phase 1 PR:** Stop reading/writing the column in application code (code-only change).
- **Phase 2 PR** (after Phase 1 is deployed and verified):
  ```sql
  ALTER TABLE my_table DROP COLUMN IF EXISTS old_column;
  ```

---

## After Writing the Migration

Update these places:
1. `README.md` — add a row to the migrations table and update the migration count
2. `tests/e2e/src/test/java/com/autotrading/e2e/FlywayMigrationTest.java` — update the expected
   applied migration count assertion
3. Add any new tables to the "Tables by service" section in `README.md`
