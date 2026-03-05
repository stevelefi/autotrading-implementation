# Agent Runtime Contract

## Owner Team
Trading Core

## Responsibilities
- Consume routed trade events and generate strategy decisions.
- Validate local preconditions.
- Invoke risk evaluation over gRPC.

## Inbound Topic
`trade.events.routed.v1`

## Outbound Interface
gRPC `RiskDecisionService.EvaluateSignal`

### Request Payload (EvaluateSignal)
```json
{
  "request_context": {
    "trace_id": "uuid",
    "request_id": "req-20260303-000123",
    "idempotency_key": "sig-20260303-000123",
    "principal_id": "svc-agent-runtime"
  },
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "signal_id": "sig-20260303-000123",
  "side": "BUY",
  "qty": 10,
  "strategy_ts": "2026-03-03T21:00:00Z",
  "order_type": "MKT",
  "time_in_force": "DAY",
  "reason": "momentum_breakout",
  "trade_event_id": "tev-20260303-000912",
  "raw_event_id": "raw-20260303-000912",
  "origin_source_type": "EXTERNAL_SYSTEM",
  "origin_source_event_id": "tv-alert-778912",
  "source_system": "agent-runtime-service"
}
```

## Contract Checks
- Reject if `qty &lt;= 0`.
- Reject if `instrument_id` missing.
- Reject if idempotency key missing.
- Always set `request_context.principal_id=svc-agent-runtime`.
- Preserve lineage fields from routed trade event (`trade_event_id`, `raw_event_id`, origin source fields).
- Require `origin_source_event_id` when `origin_source_type=EXTERNAL_SYSTEM`.

## SLO
- gRPC call to risk service p95 &lt;= 40 ms from strategy decision.

## Failure Behavior
- If risk service is unavailable, retry transient errors with same `idempotency_key` under bounded deadline.
- If retries fail, emit runtime diagnostic event and halt strategy action for that signal.
- If local config invalid, emit `risk.events.v1` warning and skip signal.
