# Performance Service Contract

## Owner Team
Data Platform

## Responsibilities
- Consume fills and statuses.
- Update positions and PnL snapshots.
- Publish derived events for dashboard and monitoring.

## Inbound Topics
- `fills.executed.v1`
- `orders.status.v1`

## Outbound Topics
- `positions.updated.v1`
- `pnl.snapshots.v1`

## Position Update Payload
```json
{
  "event_id": "uuid",
  "event_type": "positions.updated",
  "event_version": 1,
  "occurred_at": "2026-03-03T21:00:06Z",
  "trace_id": "uuid",
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "payload": {
    "net_qty": 10,
    "avg_cost": 61.22,
    "realized_pnl": 0.0,
    "unrealized_pnl": 0.0,
    "mark_price": 61.22
  }
}
```

## PnL Snapshot Contract
- Snapshot cadence: every fill + scheduled interval (default 5s)
- Futures PnL formula uses `multiplier` from instrument master.

## SLO
- Position update event p95 &lt;= 1 second after fill event arrival.

## Failure Behavior
- Missing instrument metadata => emit risk event and skip PnL calc for that event.
