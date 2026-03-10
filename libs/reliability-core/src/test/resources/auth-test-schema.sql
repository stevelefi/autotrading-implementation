-- auth-test-schema.sql: minimal in-memory H2 schema for ApiKeyAuthenticator / BrokerAccountCache tests

CREATE TABLE IF NOT EXISTS accounts (
    account_id   VARCHAR NOT NULL PRIMARY KEY,
    display_name VARCHAR NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agents (
    agent_id     VARCHAR NOT NULL PRIMARY KEY,
    account_id   VARCHAR NOT NULL REFERENCES accounts(account_id),
    display_name VARCHAR NOT NULL,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_api_keys (
    key_hash    VARCHAR NOT NULL PRIMARY KEY,
    account_id  VARCHAR NOT NULL REFERENCES accounts(account_id),
    generation  INT     NOT NULL DEFAULT 1,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS broker_accounts (
    broker_account_id   VARCHAR NOT NULL PRIMARY KEY,
    agent_id            VARCHAR NOT NULL UNIQUE REFERENCES agents(agent_id),
    broker_id           VARCHAR NOT NULL DEFAULT 'ibkr',
    external_account_id VARCHAR NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
