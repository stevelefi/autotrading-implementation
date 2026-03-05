# 03 Domain Model

## Primary Aggregates
- `IngressRawEvent`
- `RoutedTradeEvent`
- `Agent`
- `Signal`
- `OrderIntent`
- `OrderLedger`
- `BrokerOrder`
- `Execution`
- `Position`
- `PnlSnapshot`
- `PolicyDecision`
- `ReconciliationRun`

## Keys
- `ingress_event_id`
- `raw_event_id`
- `trade_event_id`
- `agent_id`
- `instrument_id`
- `order_intent_id`
- `idempotency_key`
- `exec_id`
- `trace_id`

## Invariants
- One `idempotency_key` maps to one ingress acceptance (`ingress_event_id`).
- One `raw_event_id` maps to at most one `trade_event_id`.
- One `idempotency_key` maps to one order intent.
- One order intent maps to max one broker submission path.
- Execution with same `exec_id` is processed once.
- Unknown order state implies frozen trading mode.
