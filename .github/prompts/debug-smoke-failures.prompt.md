# Debug Smoke Failures

Use this prompt when `python3 scripts/test.py smoke` (or `python3 scripts/smoke_local.py` directly) exits non-zero or a phase reports FAIL.

---

## Decision Tree

### Phase 0 — Auth DB Seed fails

- Is the Postgres container running? `docker ps | grep postgres`
- Did Flyway run V10? `docker logs flyway-init-1 2>&1 | grep V10`
- Does `account_api_keys` table exist?
  ```sql
  \dt account_api_keys
  ```
- Did the seed INSERT succeed?
  ```sql
  SELECT account_id, active FROM accounts WHERE account_id = 'acc-smoke-test';
  ```
- Did `ApiKeyAuthenticator` load the new key? Check `principal_id` in ingress-gateway logs:
  ```
  python3 scripts/stack.py logs --service ingress-gateway-service | grep "api-key-cache"
  ```
- Wait 70 s for the 60 s cache refresh, then re-run Phase 0 manually.

---

### Phase 1 — Readiness fails (one or more services not UP)

Identify which service(s) are failing:
```bash
for svc in ingress-gateway event-processor agent-runtime risk-service \
           order-service ibkr-connector performance-service monitoring-api; do
  curl -s http://localhost:<port>/actuator/health/readiness | python3 -m json.tool
done
```

Port map (see `docker-compose.yml`):
- ingress-gateway: 8080
- event-processor: 8081
- agent-runtime: 8082
- risk-service: 8083
- order-service: 8084
- ibkr-connector: 8085
- performance-service: 8086
- monitoring-api: 8087

Common causes:
- DB `HikariPool-1` timeout — Postgres container not ready yet; retry after 30 s
- Flyway `checksum mismatch` — you edited an existing migration; see Rule 7 in AGENTS.md
- Kafka `LEADER_NOT_AVAILABLE` — Redpanda not yet elected; retry after 60 s
- gRPC bind failure — port conflict; check `lsof -i :19091`

---

### Phase 2 — Idempotency fails

Check idempotency tables:
```sql
SELECT client_event_id, event_id, created_at
FROM   idempotency_records
ORDER BY created_at DESC LIMIT 10;
```

If the same `client_event_id` is returning a **different** `event_id` on the second call:
- `IdempotencyService` is not finding the first record — check DB connection isolations
- Outbox is not flushing — check `outbox_events WHERE published = FALSE`

---

### Phase 3 — Command path fails (broker submit count not incrementing)

Trace the gRPC chain:
```bash
# Check if risk produced a risk_decision
psql ... -c "SELECT * FROM risk_decisions ORDER BY created_at DESC LIMIT 5;"

# Check if order_intents were created
psql ... -c "SELECT order_intent_id, status FROM order_intents ORDER BY created_at DESC LIMIT 5;"

# Check broker_orders
psql ... -c "SELECT order_ref, status FROM broker_orders ORDER BY created_at DESC LIMIT 5;"
```

If order intent exists but broker_orders is empty: ibkr-connector is not being called.
- Check `order-service` logs for `StatusRuntimeException` from gRPC call
- Check ibkr-simulator is running: `docker ps | grep ibkr`

---

### Phase 4 — Timeout/Freeze drill fails

The phase POSTs an order and waits up to 90 s for `trading_mode=FROZEN`.

- Check `system_controls` table: `SELECT trading_mode FROM system_controls;`
- Check `system.alerts.v1` Kafka topic:
  ```bash
  docker exec redpanda rpk topic consume system.alerts.v1 --num 5
  ```
- Check `OrderTimeoutWatchdogLifecycle` logs in order-service for 60 s expiry log

If trading_mode is already `FROZEN` from a previous run, you must reset it:
```sql
UPDATE system_controls SET trading_mode = 'LIVE', updated_at = now();
```

---

### Phase 5 — Async Kafka pipeline fails

End-to-end path: ingress HTTP → `ingress.events.normalized.v1` → event-processor → agent-runtime
→ risk → order → ibkr → fills topic → performance.

Check Kafka consumer lag:
```bash
docker exec redpanda rpk group list
docker exec redpanda rpk group describe <consumer-group>
```

Check consumer_inbox for stuck records:
```sql
SELECT consumer_id, status, attempts, next_retry_at
FROM   consumer_inbox
WHERE  status != 'PROCESSED'
ORDER BY created_at DESC LIMIT 20;
```

---

### Phase 6 — Auth edge cases fail

Expected responses:
- Missing header → 400
- `X-Not-Bearer foo` → 400
- `Bearer unknown-key` → 401
- Valid key + wrong agent → 403
- Valid key + owned agent → 202

If 401 when you expect 202: the smoke key hash may not be loaded yet. The smoke suite seeds it
in Phase 0 and then waits for the cache to warm. If the wait is too short increase
`AUTH_CACHE_WARM_WAIT_MS` in `smoke_local.py`.

If 403 when you expect 202: the `agent_id` in the request does not belong to the account that
owns the smoke key. Check the seed in `seed_smoke_auth_db()`.

---

## General Tips

```bash
# Re-run the smoke suite
python3 scripts/test.py smoke

# Run only the Maven suite (no stack needed)
python3 scripts/test.py unit
python3 scripts/test.py coverage
python3 scripts/test.py e2e

# Run one module's unit tests quickly
python3 scripts/test.py unit --module services/ingress-gateway-service

# Tail all service logs
python3 scripts/stack.py logs

# Tail a single service
python3 scripts/stack.py logs --service ingress-gateway-service

# Query Loki by trace ID
python3 scripts/trace.py --trace-id <id>

# Query Loki by client_event_id
python3 scripts/trace.py --client-event-id <id>

# Full restart (infra stays up, app rebuilds)
python3 scripts/stack.py restart-app
```
