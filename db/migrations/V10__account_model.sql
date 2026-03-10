-- V10: account/agent/api-key/broker-account model.
--      Closes principal_id="anonymous" and principalJson=null TODOs in IngressService.
--      Supports multi-account, per-agent IBKR sub-account routing, and two-key
--      rotation overlap windows (no UNIQUE on account_id in account_api_keys).

-- -----------------------------------------------------------------------
-- 1. accounts — top-level billing / ownership entity
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    account_id   TEXT        PRIMARY KEY,
    display_name TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------
-- 2. agents — trading robots owned by an account
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agents (
    agent_id     TEXT        PRIMARY KEY,
    account_id   TEXT        NOT NULL REFERENCES accounts (account_id),
    display_name TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agents_account_id ON agents (account_id);

-- -----------------------------------------------------------------------
-- 3. account_api_keys — hashed bearer tokens for authentication
--    No UNIQUE on account_id: supports two-key rotation overlap window.
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS account_api_keys (
    key_hash   TEXT        PRIMARY KEY,
    account_id TEXT        NOT NULL REFERENCES accounts (account_id),
    generation INT         NOT NULL DEFAULT 1,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,                        -- NULL = never expires
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_account_api_keys_account_id ON account_api_keys (account_id);

-- -----------------------------------------------------------------------
-- 4. broker_accounts — maps one agent to one IBKR sub-account
--    UNIQUE on agent_id enforces one broker account per agent.
-- -----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS broker_accounts (
    broker_account_id    TEXT        PRIMARY KEY,
    agent_id             TEXT        NOT NULL UNIQUE REFERENCES agents (agent_id),
    broker_id            TEXT        NOT NULL DEFAULT 'ibkr',
    external_account_id  TEXT        NOT NULL,          -- e.g. "DU123456"
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_broker_accounts_agent_id ON broker_accounts (agent_id);

-- -----------------------------------------------------------------------
-- Dev seed (idempotent) — local development only.
--   API key SHA-256("dev-api-key-do-not-use-in-production")
--      = 3370ff290c7818d3886e91c10f85b48ba2562b3496a0237db94f1128e3531631
-- -----------------------------------------------------------------------
INSERT INTO accounts (account_id, display_name, active, created_at)
VALUES ('acc-local-dev', 'Local Dev Account', TRUE, now())
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO agents (agent_id, account_id, display_name, active, created_at)
VALUES ('agent-local', 'acc-local-dev', 'Local Dev Agent', TRUE, now())
ON CONFLICT (agent_id) DO NOTHING;

INSERT INTO account_api_keys (key_hash, account_id, generation, active, expires_at, created_at)
VALUES ('3370ff290c7818d3886e91c10f85b48ba2562b3496a0237db94f1128e3531631',
        'acc-local-dev', 1, TRUE, NULL, now())
ON CONFLICT (key_hash) DO NOTHING;

INSERT INTO broker_accounts (broker_account_id, agent_id, broker_id, external_account_id, active, created_at)
VALUES ('ba-local-dev', 'agent-local', 'ibkr', 'DU123456', TRUE, now())
ON CONFLICT (broker_account_id) DO NOTHING;
