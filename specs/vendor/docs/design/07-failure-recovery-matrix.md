# 07 Failure and Recovery Matrix

| Failure | Detection | Immediate Action | Recovery Path | Owner |
|---|---|---|---|---|
| Broker callback missing >60s | deadline monitor | freeze + mark unknown | reconciliation run | Trading Core |
| Connector process crash | heartbeat/health check | freeze openings | restart + reconcile | Broker Team |
| Kafka outage | producer errors/lag | continue DB writes via outbox | replay backlog after restore | Data Platform |
| Postgres failover | db health checks | hold writes, freeze if needed | recover and replay outbox | Data Platform |
| OPA unavailable | health endpoint | fail-closed risk reject | restore sidecar/bundle rollback | Policy Team |
| Project sync workflow failure | GitHub Actions failure | pause automation merge gate if needed | rerun idempotent sync | DevEx |
