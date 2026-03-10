# Broker Health Checking and Circuit Breaker

This document describes how the system detects whether the IBKR broker is available, how that
state is persisted and cached, and how ingress-gateway and order-service use it to gate order
acceptance.

For the companion auth components that start *before* `BrokerHealthCache`, see
**[AUTH_AND_ACCOUNT_MODEL.md](AUTH_AND_ACCOUNT_MODEL.md)**.

---

## SmartLifecycle Phase Context

The table below shows the full startup order for all `SmartLifecycle` components. Request traffic
cannot reach any service until Tomcat/gRPC (phase 200+) starts, by which time all caches are warm.

| Phase | Component | Role |
|-------|-----------|------|
| 40 | `ApiKeyAuthenticator` | Loads `account_api_keys` → SHA-256 in-memory auth cache |
| 40 | `BrokerAccountCache` | Loads `broker_accounts` → `agentId → externalAccountId` map |
| **50** | **`BrokerHealthCache`** | **Polls `broker_health_status` → `brokerAvailable` boolean** ← this doc |
| 100 | `IbkrHealthProbe` | Runs `GET /v1/api/tickle` → writes transitions to `broker_health_status` |
| 200+ | Tomcat / gRPC server | Begins accepting traffic |

The `ApiKeyAuthenticator` and `BrokerAccountCache` at phase 40 ensure that authentication and
broker-account routing caches are populated before the broker health cache starts at phase 50.

---

## Overview

```
ibkr-connector-service                       shared DB            ingress-gateway / order-service
─────────────────────────────────────────    ──────────────────   ───────────────────────────────
IbkrHealthProbe (tickle every 30 s)
  │  transition only (UP/DOWN/UNKNOWN)
  ▼
BrokerHealthPersister ──── upsert ────────▶  broker_health_status ◀── poll every 10–15 s ── BrokerHealthCache
                                             (TEXT status column)                                 │
                                                                                                  ▼
                                                                              isBrokerAvailable() ◀── circuit-breaker check
                                                                              ↓ false → 503 / COMMAND_FAILED
```

---

## 1. Detecting Broker Connectivity — `IbkrHealthProbe`

**Location:** `services/ibkr-connector-service` — `IbkrHealthProbe` (`SmartLifecycle` phase 100)

`IbkrHealthProbe` runs a single background thread that calls `GET /v1/api/tickle` (the IBKR
Client Portal keep-alive endpoint) on a fixed interval (default: 30 s, configurable via
`ibkr.health.tickle-interval-ms`).

### Status transitions

The probe maintains a `volatile BrokerStatus status` field with three values:

| Value | Meaning |
|-------|---------|
| `UNKNOWN` | Startup default; probe hasn't completed its first tickle yet |
| `UP` | Last tickle returned `authenticated: true` |
| `DOWN` | Last tickle returned non-authenticated, timed out, or threw an exception |

The probe fires its `onTransition(BrokerStatus)` callback **only when the status changes** (e.g.
`UNKNOWN→UP`, `UP→DOWN`, `DOWN→UP`). Repeated successful tickles while already UP are silent.
This prevents write amplification on the DB: with a 30 s tickle interval, only actual outages
generate DB writes.

### Simulator mode

When `ibkr.simulator-mode=true` (local dev / CI), `UNKNOWN` is also treated as UP so startup
ordering races don't block the service.

### Source reference

- [IbkrHealthProbe.java](../services/ibkr-connector-service/src/main/java/com/autotrading/services/ibkr/client/IbkrHealthProbe.java)

---

## 2. Persisting State — `BrokerHealthPersister`

**Location:** `services/ibkr-connector-service` — `BrokerHealthPersister`

When `IbkrHealthProbe` fires a transition, it calls `BrokerHealthPersister.onTransition(newStatus)`,
which upserts the shared `broker_health_status` table:

```sql
INSERT INTO broker_health_status (broker_id, status, updated_at)
VALUES (:brokerId, :status, :updatedAt)
ON CONFLICT (broker_id) DO UPDATE
    SET status     = EXCLUDED.status,
        updated_at = EXCLUDED.updated_at;
```

**Failure behaviour:** DB errors are logged and swallowed. A failed persist does not interrupt the
probe loop or broker operations. The worst case is that reader services keep a stale cached value
for one extra cache refresh cycle (≤ 15 s).

### Source reference

- [BrokerHealthPersister.java](../services/ibkr-connector-service/src/main/java/com/autotrading/services/ibkr/health/BrokerHealthPersister.java)

---

## 3. The Shared DB Table — `broker_health_status`

**Migration:** `db/migrations/V9__broker_health_status.sql`

```sql
CREATE TABLE IF NOT EXISTS broker_health_status (
    broker_id   TEXT        PRIMARY KEY,         -- always 'ibkr'
    status      TEXT        NOT NULL,            -- 'UP' | 'DOWN' | 'UNKNOWN'
    detail_json TEXT,                            -- optional error detail
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seeded on first Flyway apply so readers always find exactly one row.
INSERT INTO broker_health_status (broker_id, status, updated_at)
VALUES ('ibkr', 'UNKNOWN', now())
ON CONFLICT (broker_id) DO NOTHING;
```

The table has exactly one row (`broker_id = 'ibkr'`). All reader services query by primary key —
the lookup is O(1) and cheap enough to poll frequently.

---

## 4. Caching the State — `BrokerHealthCache`

**Location:** `libs/reliability-core` — `BrokerHealthCache` (`SmartLifecycle` phase **50**)

Each service that enforces the circuit breaker (`ingress-gateway-service`, `order-service`)
declares a `BrokerHealthCache` bean. The cache:

1. Starts a single daemon thread (`broker-health-cache`).
2. Polls `SELECT status FROM broker_health_status WHERE broker_id = 'ibkr'` at a configurable
   interval (see §4.1 below).
3. Updates a `volatile boolean brokerAvailable` field on every poll.

The boolean is the **only thing** callers read on the hot path — no DB round-trip per request.

### 4.1 Cache configuration

| Service | Property | Default |
|---------|----------|---------|
| `ingress-gateway-service` | `broker.health.cache.refresh-interval-ms` | 15 000 ms |
| `order-service` | `broker.health.cache.refresh-interval-ms` | 10 000 ms |

### 4.2 Failure / edge-case policy

| Situation | Behaviour | Rationale |
|-----------|-----------|-----------|
| Row not yet present (Flyway not yet applied) | Treated as **available** | Optimistic — don't block all trading on a missing row |
| DB query throws / connection reset | Keep **last known value** | A DB blip must not stop currently-green trading |
| `status = 'UNKNOWN'` (startup seed row) | Treated as **available** | Unknown ≠ known-down |
| `status = 'DOWN'` | Treated as **unavailable** | The only condition that opens the circuit |

### 4.3 SmartLifecycle phasing

`BrokerHealthCache` starts at phase **50**, before the embedded Tomcat / gRPC server lifecycle
(typically phase 200+). This guarantees the first DB poll has completed and `brokerAvailable` is
set to an accurate value before the service begins accepting real traffic.

### Source reference

- [BrokerHealthCache.java](../libs/reliability-core/src/main/java/com/autotrading/libs/health/BrokerHealthCache.java)

---

## 5. The Circuit Breaker at Ingress — `IngressService`

**Location:** `services/ingress-gateway-service` — `IngressService`

The `BooleanSupplier brokerHealthGate` bean (declared in `IngressGatewayConfiguration`) wraps
`brokerHealthCache::isBrokerAvailable` and is injected into `IngressService`.

On every inbound HTTP request, **before the idempotency claim**, the service checks:

```java
if (!brokerHealthCheck.getAsBoolean()) {
    log.warn("ingress rejecting event — broker known DOWN clientEventId={}",
        request.client_event_id());
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "broker is currently unavailable — retry later");
}
```

**HTTP response when open:** `503 Service Unavailable`

The check is placed **before** the idempotency claim deliberately. If it were placed after, the
`client_event_id` would be consumed by the idempotency table during an outage, and the client's
retry after recovery would be silently replayed rather than forwarded — losing the order. By
rejecting pre-claim, the `client_event_id` stays unclaimed and the client can retry unchanged.

### Source references

- [IngressService.java](../services/ingress-gateway-service/src/main/java/com/autotrading/services/ingress/core/IngressService.java)
- [IngressGatewayConfiguration.java](../services/ingress-gateway-service/src/main/java/com/autotrading/services/ingress/IngressGatewayConfiguration.java)

---

## 6. The Circuit Breaker at Order-Service — `OrderSafetyEngine`

**Location:** `services/order-service` — `OrderSafetyEngine`

Order-service has its own `BrokerHealthCache` (polling every 10 s) and its own check in
`createOrderIntent`, again placed **before** the idempotency claim:

```java
if (!brokerHealthCheck.getAsBoolean()) {
    log.warn("order-service rejecting createOrderIntent — broker known DOWN clientEventId={}",
        request.getRequestContext().getClientEventId());
    return CreateOrderIntentResponse.newBuilder()
        .setStatus(CommandStatus.COMMAND_STATUS_FAILED)
        .addReasons("broker known DOWN — retry when broker recovers")
        .build();
}
```

**gRPC response when open:** `COMMAND_STATUS_FAILED`

Order-service also maintains a separate `TradingMode` (NORMAL / CIRCUIT_OPEN / FROZEN) checked
immediately before the broker health gate. A `FROZEN` mode (triggered by the 60 s timeout
watchdog) returns `COMMAND_STATUS_REJECTED` and takes precedence over the broker health gate.

### Source references

- [OrderSafetyEngine.java](../services/order-service/src/main/java/com/autotrading/services/order/core/OrderSafetyEngine.java)
- [OrderRuntimeConfiguration.java](../services/order-service/src/main/java/com/autotrading/services/order/runtime/OrderRuntimeConfiguration.java)

---

## 7. End-to-End Flow Summary

```
ibkr-connector-service (every 30 s)
  IbkrHealthProbe.runTickle()
    → calls GET /v1/api/tickle
    → status change detected?
        YES → BrokerHealthPersister.onTransition(DOWN)
                → upsert broker_health_status SET status='DOWN'

ingress-gateway-service / order-service (every 10–15 s, background)
  BrokerHealthCache.refresh()
    → SELECT status FROM broker_health_status WHERE broker_id='ibkr'
    → status='DOWN' → brokerAvailable = false
    → log transition: "BrokerHealthCache transition AVAILABLE → DOWN"

Next inbound HTTP/gRPC request:
  IngressService.accept() or OrderSafetyEngine.createOrderIntent()
    → brokerHealthCheck.getAsBoolean() returns false
    → 503 / COMMAND_STATUS_FAILED — request rejected, client_event_id NOT consumed
    → client retries after broker recovers → same client_event_id accepted normally
```

---

## 8. Observability

| What to look for | Where |
|-----------------|-------|
| Transition log line (probe) | `IbkrHealthProbe: broker DOWN — tickle failed` or `broker UP (transition from …)` |
| Transition log line (cache) | `BrokerHealthCache transition AVAILABLE → DOWN` |
| Rejection log line (ingress) | `ingress rejecting event — broker known DOWN clientEventId=…` |
| Rejection log line (order) | `order-service rejecting createOrderIntent — broker known DOWN clientEventId=…` |
| Persist log line | `broker health persisted brokerId=ibkr status=DOWN` |
| DB direct query | `SELECT broker_id, status, updated_at FROM broker_health_status;` |
| trace.py | `python3 scripts/trace.py --client-event-id <key>` — look for 503/FAILED outcome |
