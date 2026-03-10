# Authentication and Account Model

This document describes the V10 account / API-key model, the `ApiKeyAuthenticator` and
`BrokerAccountCache` lifecycle components, the per-request auth flow in `IngressService`,
and the `scripts/onboard.py` management CLI.

---

## Overview

Every HTTP request to `ingress-gateway-service` must carry a `Bearer` token:

```
Authorization: Bearer <raw-api-key>
```

The raw key is SHA-256 hashed on arrival and looked up in an in-memory cache populated by
`ApiKeyAuthenticator` (SmartLifecycle phase 40). No DB I/O happens on the hot path.

```
HTTP Authorization: Bearer <raw-key>
    │
    ▼  SHA-256(raw-key)
    ├─ cache miss (unknown hash)            → 401 Unauthorized
    ├─ no header / non-Bearer scheme        → 400 Bad Request
    ├─ agent_id not owned by accountId      → 403 Forbidden
    └─ valid                                → 202 Accepted
                                               MDC: principal_id = accountId
                                               ingress_raw_events.principal_id  = accountId
                                               ingress_raw_events.principal_json = {"accountId":…}
```

All four auth tables were introduced in `db/migrations/V10__account_model.sql`.

---

## V10 Database Schema

```sql
-- Top-level billing / ownership entity
CREATE TABLE IF NOT EXISTS accounts (
    account_id   TEXT        PRIMARY KEY,
    display_name TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Trading robots owned by an account
CREATE TABLE IF NOT EXISTS agents (
    agent_id     TEXT        PRIMARY KEY,
    account_id   TEXT        NOT NULL REFERENCES accounts (account_id),
    display_name TEXT        NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agents_account_id ON agents (account_id);

-- Hashed bearer tokens — no UNIQUE on account_id (supports two-key rotation overlap)
CREATE TABLE IF NOT EXISTS account_api_keys (
    key_hash   TEXT        PRIMARY KEY,
    account_id TEXT        NOT NULL REFERENCES accounts (account_id),
    generation INT         NOT NULL DEFAULT 1,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,          -- NULL = never expires
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_account_api_keys_account_id ON account_api_keys (account_id);

-- One IBKR sub-account per agent  (UNIQUE on agent_id)
CREATE TABLE IF NOT EXISTS broker_accounts (
    broker_account_id    TEXT        PRIMARY KEY,
    agent_id             TEXT        NOT NULL UNIQUE REFERENCES agents (agent_id),
    broker_id            TEXT        NOT NULL DEFAULT 'ibkr',
    external_account_id  TEXT        NOT NULL,   -- e.g. "DU123456"
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_broker_accounts_agent_id ON broker_accounts (agent_id);
```

### Entity Relationship

```
accounts (account_id PK)
    └── agents         (agent_id PK, account_id FK → accounts)
    └── account_api_keys (key_hash PK, account_id FK → accounts)
                                 ↑ many keys per account (key rotation)
agents
    └── broker_accounts (broker_account_id PK, agent_id UNIQUE FK → agents)
                                 ↑ one broker account per agent
```

---

## Dev Seed

V10 includes an idempotent seed block for local development. The seed creates:

| Object | Value |
|--------|-------|
| `accounts.account_id` | `acc-local-dev` |
| `agents.agent_id` | `agent-local` |
| `account_api_keys.key_hash` | `3370ff290c7818d3886e91c10f85b48ba2562b3496a0237db94f1128e3531631` |
| Raw key that hashes to the above | `dev-api-key-do-not-use-in-production` |
| `broker_accounts.external_account_id` | `DU123456` |

The smoke suite creates its own fixture (`smoke-api-key-local`) in Phase 0 — it does not rely on
the dev seed.

---

## `ApiKeyAuthenticator`

**Location:** `libs/reliability-core/com/autotrading/libs/auth/ApiKeyAuthenticator.java`
**SmartLifecycle phase:** 40 (starts before `BrokerHealthCache` at phase 50 and Tomcat/gRPC at phase 200+)

### Structure

```java
// Hot-path lookup: O(1)
ConcurrentHashMap<String keyHash, AuthenticatedPrincipal> keyCache;

// Ownership check for 403 logic
ConcurrentHashMap<String accountId, Set<String> agentIds> ownershipMap;
```

`AuthenticatedPrincipal` is a record:

```java
public record AuthenticatedPrincipal(String accountId, String keyHash, int generation) {}
```

### Cache Refresh Query

```sql
SELECT k.key_hash, k.account_id, k.generation, a.agent_id
FROM   account_api_keys k
JOIN   accounts         acc ON acc.account_id = k.account_id AND acc.active = TRUE
LEFT JOIN agents        a   ON a.account_id   = k.account_id AND a.active   = TRUE
WHERE  k.active = TRUE
  AND  (k.expires_at IS NULL OR k.expires_at > now())
```

The cache is rebuilt every `refreshIntervalMs` (default 60 000 ms) by a background thread named
`api-key-cache`. On DB error the last known cache is **kept** (fail-open: prefer known-good state
over outright denial of service).

### API

```java
// Returns empty if the hash is not in the cache
Optional<AuthenticatedPrincipal> authenticate(String rawKey);

// Returns true if agentId is in the ownership set for accountId
boolean isAgentOwnedBy(String agentId, String accountId);
```

### Key rotation

`account_api_keys` has **no** `UNIQUE` constraint on `account_id`, so two keys can be active
simultaneously during a rotation window. Both keys hash to different SHA-256 values and will
both be present in the cache. Revoke the old key (set `active = FALSE`) once clients have migrated.

---

## `BrokerAccountCache`

**Location:** `libs/reliability-core/com/autotrading/libs/auth/BrokerAccountCache.java`
**SmartLifecycle phase:** 40

The cache maps each `agent_id` to its IBKR `external_account_id` so the connector can route
orders to the correct IBKR sub-account without a DB query per order.

```java
ConcurrentHashMap<String agentId, String externalAccountId> cache;
```

A background thread named `broker-account-cache` refreshes the map every `refreshIntervalMs`
(default 60 000 ms) by querying `broker_accounts WHERE active = TRUE`.

```java
// Returns externalAccountId or falls back to ibkr.cp.account-id config property
String resolveExternalAccountId(String agentId);
```

---

## SmartLifecycle Phase Order

| Phase | Component | Role |
|-------|-----------|------|
| 40 | `ApiKeyAuthenticator` | Loads `account_api_keys` → `keyCache` + `ownershipMap` |
| 40 | `BrokerAccountCache` | Loads `broker_accounts` → `agentId → externalAccountId` |
| 50 | `BrokerHealthCache` | Polls `broker_health_status` → `brokerAvailable` boolean |
| 100 | `IbkrHealthProbe` | Runs `GET /v1/api/tickle` → writes transitions to `broker_health_status` |
| 200+ | Tomcat / gRPC server | Begins accepting traffic — **all three caches already warm** |

**Startup guarantee:** by the time ingress-gateway starts accepting HTTP, `ApiKeyAuthenticator`
has completed its first load, so no request can arrive before the auth cache is populated.

---

## IngressService Auth Flow

```
IngressService.handleEvent(request)
    │
    ├─ 1. extract Authorization header
    │       missing or blank            → throw BadRequestException("Authorization header required")
    │       does not start with "Bearer " → throw BadRequestException("Bearer token required")
    │
    ├─ 2. rawKey = header.substring("Bearer ".length())
    │    principal = apiKeyAuthenticator.authenticate(rawKey)
    │       empty Optional              → throw UnauthorizedException("Invalid API key")
    │
    ├─ 3. agentId = request.getAgentId()
    │    isAgentOwnedBy(agentId, principal.accountId())
    │       false                       → throw ForbiddenException("Agent not owned by account")
    │
    └─ 4. valid → proceed:
            MDC.put("principal_id", principal.accountId())
            rawEvent.principalId  = principal.accountId()
            rawEvent.principalJson = serialize(principal)
            → idempotency claim → outbox → 202 Accepted
```

### HTTP Status Codes

| Condition | HTTP Status |
|-----------|------------|
| Missing `Authorization` header | 400 Bad Request |
| Header does not start with `Bearer ` | 400 Bad Request |
| SHA-256 of raw key not in cache | 401 Unauthorized |
| Agent not in account's agent set | 403 Forbidden |
| Valid key + owned agent | 202 Accepted |

---

## MDC Fields

| Field | Value |
|-------|-------|
| `principal_id` | `accountId` from `AuthenticatedPrincipal` — populated on every accepted ingress event |
| `agent_id` | `agentId` from the request body |

Both fields are included in all structured log lines for the lifetime of the request thread.

---

## `scripts/onboard.py` — Management CLI

Manages the V10 tables directly in the local Postgres container (or any DB via `--db-url`).

```bash
# Account management
python3 scripts/onboard.py account create <account-id> "<Display Name>"
python3 scripts/onboard.py account list

# Agent management
python3 scripts/onboard.py agent create <agent-id> <account-id> "<Display Name>"
python3 scripts/onboard.py agent list <account-id>

# API key management
python3 scripts/onboard.py apikey generate <account-id>          # random key — shown once
python3 scripts/onboard.py apikey create   <account-id> <raw-key>  # register a known key
python3 scripts/onboard.py apikey list     <account-id>
python3 scripts/onboard.py apikey revoke   <sha256-hash>

# Broker account mapping
python3 scripts/onboard.py broker create <agent-id> <external-account-id>  # e.g. DU123456
python3 scripts/onboard.py broker list
```

The script stores only the **SHA-256 hash** of the raw key; the raw key is shown exactly once
(on `generate` or `create`) and never stored.

---

## Observability

| Signal | Location |
|--------|----------|
| `principal_id` on every ingress log line | Loki / `scripts/trace.py --trace-id` |
| Auth cache size / last-refresh at `/actuator/info` | ingress-gateway metrics |
| `401 / 403` response counter | Prometheus `http_server_requests_seconds{status=401}` |
| Smoke Phase 6 coverage | `reports/blitz/e2e-results/smoke-local-<ts>.md` |

---

## Key Rotation Procedure

1. Create a new key for the account (`onboard.py apikey generate <account-id>`).
2. Distribute the new raw key to clients.
3. Wait one `refreshIntervalMs` (≤ 60 s) for `ApiKeyAuthenticator` to load it.
4. Clients migrate to the new key.
5. Revoke the old key hash (`onboard.py apikey revoke <old-hash>`).
6. The next cache refresh will drop the old hash.
