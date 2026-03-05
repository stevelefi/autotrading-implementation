# 08 Capacity and Scaling

## Initial Sizing Assumptions
- Agents: 10-100
- Signals/min: 50-500
- Orders/min: 10-200
- Callback burst: market open/close spikes

## Scaling Strategy
- Stateless services horizontally scale except connector writer.
- Kafka partitions keyed by `agent_id`.
- Postgres tuned for write-heavy ledger + indexed read models.

## Bottlenecks to Watch
- OPA evaluation latency
- Connector callback processing lag
- Outbox backlog age
- Reconciliation job duration
