# Order Service Contract

## Owner Team
Trading Core

## Responsibilities
- Build order intents from risk outcomes received over gRPC.
- Persist order ledger state transitions.
- Issue broker submit/cancel/replace commands over gRPC.
- Enforce 60-second broker-status deadline.

## Inbound Interfaces
- gRPC `OrderCommandService.CreateOrderIntent`
- Kafka `orders.status.v1` (from broker callback normalization)

## Outbound Interfaces
- gRPC `BrokerCommandService.SubmitOrder`
- gRPC `BrokerCommandService.CancelOrder`
- gRPC `BrokerCommandService.ReplaceOrder`
- `system.alerts.v1` (timeout/freeze)

## Order Intent Payload (SubmitOrder Request)
```json
{
  "request_context": {
    "trace_id": "uuid",
    "request_id": "req-order-20260303-000123",
    "idempotency_key": "sig-20260303-000123",
    "principal_id": "svc-order"
  },
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "order_intent_id": "ord_9f1a...",
  "side": "BUY",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "submission_deadline_ms": 60000
}
```

## Timeout Contract
- If broker status not observed by `submission_deadline`:
  - transition to `UNKNOWN_PENDING_RECON`
  - set `trading_mode=FROZEN`
  - emit `system.alerts.v1` (`severity=CRITICAL`)

## Idempotency Contract
- Duplicate `idempotency_key` returns existing `order_intent_id`.
- No duplicate order intent creation or duplicate effective submit command.

## SLO
- Intent creation latency p95 &lt;= 30 ms once decision command is received.

## Failure Behavior
- DB failure => no intent creation (transaction rollback).
- gRPC submit failure => bounded retry for transient errors, preserving `idempotency_key`.
- Kafka callback/status lag => maintain state as pending until timeout path triggers freeze.
