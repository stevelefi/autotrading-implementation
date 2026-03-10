# Plan: Account / Agent / Authentication Implementation

**TL;DR** — Three layers of work: (1) a Flyway migration creating four new tables (`accounts`, `agents`, `account_api_keys`, `broker_accounts`); (2) two new library classes — `ApiKeyAuthenticator` (same `SmartLifecycle` cache pattern as `BrokerHealthCache`) and `BrokerAccountCache` — both in `libs/reliability-core`; (3) surgical wiring into `IngressService` (auth + agent-ownership check) and `IbkrRuntimeConfiguration` (agent → sub-account routing). No gRPC contracts change. The `principal_id = "anonymous"` TODO and the `principalJson = null` TODO are both closed.

---

## Steps

### 1. V10 migration — `db/migrations/V10__account_model.sql`

Four tables following existing conventions (TEXT PKs, TIMESTAMPTZ, no ON DELETE CASCADE, IF NOT EXISTS guards, `idx_` prefix indexes):

- `accounts(account_id PK, display_name, active BOOLEAN, created_at)`
- `agents(agent_id PK, account_id FK→accounts, display_name, active BOOLEAN, created_at)` + index `idx_agents_account_id`
- `account_api_keys(key_hash PK, account_id FK→accounts, generation INT, active BOOLEAN, expires_at TIMESTAMPTZ nullable, created_at)` + index `idx_account_api_keys_account_id`
  - No UNIQUE on `account_id` — allows two active keys during rotation overlap window
- `broker_accounts(broker_account_id PK, agent_id FK→agents UNIQUE, broker_id TEXT, external_account_id TEXT, active BOOLEAN, created_at)` + index `idx_broker_accounts_agent_id`
  - UNIQUE on `agent_id` enforces one broker account per agent

Dev seed block at the bottom (idempotent via `ON CONFLICT DO NOTHING`): one `acc-local-dev` account, one `agent-local` agent linked to it, one `account_api_keys` row with the SHA-256 of `dev-api-key-do-not-use-in-production`, one `broker_accounts` row pointing `agent-local` → `DU123456`.

---

### 2. `AuthenticatedPrincipal` record — `libs/reliability-core/src/main/java/com/autotrading/libs/auth/AuthenticatedPrincipal.java`

```java
record AuthenticatedPrincipal(String accountId, String keyHash, int generation)
```

Used as the return type of `ApiKeyAuthenticator.authenticate()`. Kept minimal — enough to populate `principal_id` and `principal_json` on the raw event.

---

### 3. `ApiKeyAuthenticator` — `libs/reliability-core/src/main/java/com/autotrading/libs/auth/ApiKeyAuthenticator.java`

Modelled identically to `BrokerHealthCache` — `SmartLifecycle` phase **40** (before phase 50 so it is warm before any other cache starts):

- `ConcurrentHashMap<String keyHash, AuthenticatedPrincipal>` — hot path, no I/O
- `agentsByAccount: ConcurrentHashMap<String accountId, Set<String> agentIds>` — for ownership check
- Background thread `api-key-cache` polls every `refreshIntervalMs` (default 60 000 ms)
- Refresh query:
  ```sql
  SELECT k.key_hash, k.account_id, k.generation, a.agent_id
  FROM account_api_keys k
  JOIN accounts acc ON acc.account_id = k.account_id
  LEFT JOIN agents a ON a.account_id = k.account_id
  WHERE k.active = TRUE
    AND acc.active = TRUE
    AND (k.expires_at IS NULL OR k.expires_at > now())
  ```
- `Optional<AuthenticatedPrincipal> authenticate(String rawKey)` — SHA-256 raw key, look up in map
- `boolean isAgentOwnedBy(String agentId, String accountId)` — look up in `agentsByAccount`
- Failure policy: on DB error keep last known map (same as `BrokerHealthCache` — a DB blip must not lock out all trading)

---

### 4. `BrokerAccountCache` — `libs/reliability-core/src/main/java/com/autotrading/libs/auth/BrokerAccountCache.java`

Same `SmartLifecycle` pattern, phase **40**:

- `ConcurrentHashMap<String agentId, String externalAccountId>` — hot path
- Refresh query: `SELECT agent_id, external_account_id FROM broker_accounts WHERE active = TRUE`
- `Optional<String> resolveExternalAccountId(String agentId)` — O(1) lookup
- Fallback: if map has no entry for `agentId`, fall back to default account ID from config

---

### 5. Wire `ApiKeyAuthenticator` into `IngressGatewayConfiguration`

Modify `services/ingress-gateway-service/src/main/java/com/autotrading/services/ingress/IngressGatewayConfiguration.java`.

Add two new `@Bean` methods alongside the existing `brokerHealthCache` bean:
- `ApiKeyAuthenticator apiKeyAuthenticator(JdbcTemplate, @Value("${auth.api-key.cache.refresh-interval-ms:60000}") long)`
- `BooleanSupplier brokerHealthGate(...)` already exists — no change

---

### 6. Replace the auth stub in `IngressService`

Modify `services/ingress-gateway-service/src/main/java/com/autotrading/services/ingress/core/IngressService.java`.

Add `ApiKeyAuthenticator apiKeyAuthenticator` as a constructor parameter (the `@Autowired` constructor). The `validate()` method becomes:

```
1. blank check on requestId / authorization header         → 400
2. strip "Bearer " prefix from authorization header        → 400 if not Bearer format
3. authenticator.authenticate(rawKey)                      → 401 if empty
4. isAgentOwnedBy(request.agent_id(), principal.accountId()) → 403 if false
```

In `accept()`:
- Replace `"anonymous"` on `RequestContext` with `principal.accountId()`
- Replace `null` on `IngressRawEventEntity` `principalJson` arg with a small JSON string: `{"accountId":"acc-xxx","keyGeneration":1}`
- Add `MDC.put("principal_id", principal.accountId())` in `IngressController` (same place as the existing `MDC.put("agent_id", ...)`)

Both existing constructors (the test-friendly one with `() -> true` and the `@Autowired` one) stay intact — the test constructor gets a no-op `ApiKeyAuthenticator` override via a new package-private constructor that accepts it with `alwaysAuthenticated = true` behavior.

---

### 7. Wire `BrokerAccountCache` into `IbkrRuntimeConfiguration`

Modify `services/ibkr-connector-service/src/main/java/com/autotrading/services/ibkr/runtime/IbkrRuntimeConfiguration.java`.

Add:
- `BrokerAccountCache brokerAccountCache(JdbcTemplate, ...)` bean
- Pass `brokerAccountCache::resolveExternalAccountId` into `BrokerConnectorEngine` (or keep it in `IbkrRestClient` — see next step)

---

### 8. Add routing to `IbkrRestClient`

Modify `services/ibkr-connector-service/src/main/java/com/autotrading/services/ibkr/client/IbkrRestClient.java`.

Change the constructor to accept a `Function<String, String> accountResolver` instead of a bare `String accountId`. In `submitOrder()`:

```java
String resolvedAccount = accountResolver.apply(agentId);  // from BrokerAccountCache
// if empty → falls back to cpAccountId default
uri("/v1/api/iserver/order/{accountId}", resolvedAccount)
```

`submitOrder()` gains `String agentId` as its first parameter (already available from `SubmitOrderRequest.agent_id` flowing through `BrokerConnectorEngine`).

---

### 9. Add `auth.api-key.cache.refresh-interval-ms` to ingress `application.yml`

Modify `services/ingress-gateway-service/src/main/resources/application.yml`:

```yaml
auth:
  api-key:
    cache:
      refresh-interval-ms: ${AUTH_API_KEY_CACHE_REFRESH_INTERVAL_MS:60000}
```

---

### 10. Add `.env.example` and `.gitignore` entry

New file `infra/local/.env.example`:

```
# Copy to .env and fill in values — never commit .env
SPRING_DATASOURCE_PASSWORD=autotrading
IBKR_CP_ACCOUNT_ID=DU123456
IBKR_CP_BASE_URL=http://ibkr-simulator:8080
```

Also add `infra/local/.env` to `.gitignore`.

---

### 11. E2E test — `tests/e2e/src/test/java/com/autotrading/e2e/AccountAuthTest.java`

Four test cases using the in-process / H2 pattern from existing tests:
- `validKeyAndOwnedAgent_returns202` — happy path
- `missingAuthorizationHeader_returns400`
- `unknownApiKey_returns401`
- `agentNotOwnedByAccount_returns403`

---

## Verification

```bash
python3 scripts/test.py unit
python3 scripts/test.py e2e
python3 scripts/check.py --fast
```

After `stack.py infra-up`, verify the dev seed row works:

```bash
curl -s -X POST http://localhost:8080/ingress/v1/events \
  -H "Authorization: Bearer dev-api-key-do-not-use-in-production" \
  -H "X-Request-Id: req-001" \
  -H "Content-Type: application/json" \
  -d '{"client_event_id":"k-001","event_intent":"TRADE_SIGNAL","agent_id":"agent-local","payload":{"instrument_id":"AAPL","side":"BUY","qty":10}}'
# expect 202
```

Query DB to confirm `principal_json` is now populated and `principal_id` is `acc-local-dev`:

```sql
SELECT event_id, agent_id, principal_json FROM ingress_raw_events ORDER BY received_at DESC LIMIT 1;
```

---

## Decisions

- `ApiKeyAuthenticator` and `BrokerAccountCache` live in `libs/reliability-core` (existing home for cross-cutting infrastructure) not in `ingress-gateway-service`, so `ibkr-connector-service` can depend on `BrokerAccountCache` without a circular module dependency
- Phase 40 for both new caches — strictly before `BrokerHealthCache` (phase 50) so the auth cache is always warm when the health cache starts and any startup-time request is properly authenticated
- No UNIQUE on `account_id` in `account_api_keys` — supports two-key rotation overlap window by design
- `submitOrder()` passes `agentId` down to `IbkrRestClient` rather than resolving at the engine level — keeps account routing concern inside the REST client where it belongs
