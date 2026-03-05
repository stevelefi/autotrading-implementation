# IBKR Connector Contract

## Owner Team
Broker Connectivity

## Responsibilities
- Single active broker writer.
- Accept order submit/cancel/replace commands over gRPC.
- Process broker status/fill callbacks.
- Publish normalized order/fill events.

## Inbound Interfaces
- gRPC `BrokerCommandService.SubmitOrder`
- gRPC `BrokerCommandService.CancelOrder`
- gRPC `BrokerCommandService.ReplaceOrder`
- IBKR callback stream (`openOrder`, `orderStatus`, `execDetails`, `commissionReport`, `error`)

## Outbound Topics
- `orders.status.v1`
- `fills.executed.v1`
- `risk.events.v1`
- `system.alerts.v1`

## Submission Contract
- One active connector instance holds lease.
- Lease loss blocks new submissions.
- Broker `order_ref` format: `{agent_id}:{order_intent_id}`
- Duplicate gRPC command with same `idempotency_key` returns prior acceptance metadata.
- `orderStatus` is advisory; `execDetails` remains authoritative for fill accounting.

## Order Status Payload
```json
{
  "event_id": "uuid",
  "event_type": "orders.status",
  "event_version": 1,
  "occurred_at": "2026-03-03T21:00:05Z",
  "trace_id": "uuid",
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "payload": {
    "order_intent_id": "ord_9f1a...",
    "broker_order_id": 120341,
    "perm_id": 902331221,
    "status": "SUBMITTED_ACKED",
    "broker_status": "Submitted",
    "remaining_qty": 10,
    "filled_qty": 0
  }
}
```

## Fill Payload
```json
{
  "event_id": "uuid",
  "event_type": "fills.executed",
  "event_version": 1,
  "occurred_at": "2026-03-03T21:00:06Z",
  "trace_id": "uuid",
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "payload": {
    "order_intent_id": "ord_9f1a...",
    "exec_id": "00012f1d.65f4...",
    "perm_id": 902331221,
    "fill_qty": 10,
    "fill_price": 61.22,
    "commission": 0.35,
    "liquidity": "ADDED"
  }
}
```

## Dedup Contract
- Duplicate callback by same `exec_id` => ignored.
- Duplicate status update with same state/version => idempotent no-op.
- Duplicate gRPC command by same `idempotency_key` => no new broker submission.

## Failure Behavior
- Broker disconnect during submit window => emit disconnect alert.
- Ambiguous callback mapping => emit risk event and force reconciliation path.
- gRPC command path unavailable => return explicit unavailable error; do not attempt unsafe best-effort fallback.

## Development Simulator Mode
For local development and CI where real IBKR is not available, connector flows can use the IBKR simulator:

- Simulator base URL: `http://ibkr-simulator:8080`
- Submit endpoint: `POST /v1/orders`
- Cancel endpoint: `POST /v1/orders/{ib_order_id}/cancel`
- Event polling endpoint: `GET /v1/events`

Recommended environment variables:
- `IBKR_MODE=SIMULATOR`
- `IBKR_SIMULATOR_BASE_URL=http://ibkr-simulator:8080`

Reference:
- [IBKR Gateway Simulator (DEV)](../IBKR_GATEWAY_SIMULATOR.md)
