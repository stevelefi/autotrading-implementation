# 06 Consistency Model

## Model
Consistency-first ledger model with idempotent eventing.

## Mechanisms
- Transactional writes to Postgres.
- Outbox table for publish reliability.
- Inbox dedupe for consumer idempotency.
- Broker dedupe with `order_ref`, `perm_id`, `exec_id`.

## Guarantees
- Effectively-once business outcome under retries and reconnects.
- Explicit freeze on uncertainty.

## Non-Goals
- Never attempt optimistic continuation during unknown state.
