# 04 Sequence Diagrams

This document is the detailed sequence reference for runtime behavior.

## How to Read These Diagrams
- Event submission edge uses `ingress-gateway-service` (WebHook/API/gRPC/WebSocket).
- Control/query plane uses REST + SSE (`monitoring-api` as edge).
- Event plane uses Kafka topics (`*.v1`).
- State plane uses PostgreSQL (authoritative lifecycle records).
- Correlation keys are mandatory: `trace_id`, `agent_id`, `order_intent_id`.

## SD-01: Normal Execution Path (Allowed Signal)
```mermaid
sequenceDiagram
  participant AR as Agent Runtime
  participant RS as Risk Service
  participant OPA as OPA
  participant OS as Order Service
  participant IC as Broker Connector
  participant IB as IBKR
  participant PS as Performance

  AR->>RS: gRPC EvaluateSignal(idempotency_key)
  RS->>OPA: evaluate(signal, limits, session)
  OPA-->>RS: ALLOW + policy metadata
  RS->>OS: gRPC CreateOrderIntent(ALLOW)
  OS->>OS: persist intent + outbox tx
  OS->>IC: gRPC SubmitOrder(order_intent_id)
  IC->>IB: placeOrder(order_ref)
  IB-->>IC: orderStatus callback(s)
  IC->>OS: orders.status.v1
  IB-->>IC: execDetails callback(s)
  IC->>PS: fills.executed.v1 (exec_id)
  PS->>PS: dedupe exec_id + update projections
```

Expected outcomes:
1. Exactly one effective order intent per signal `idempotency_key`.
2. Fill replay does not double-apply PnL due to `exec_id` dedupe.
3. UI observes the same lifecycle as persisted ledger state.

## SD-02: Risk Reject Path
```mermaid
sequenceDiagram
  participant AR as Agent Runtime
  participant RS as Risk Service
  participant OPA as OPA
  participant OS as Order Service
  participant MA as Monitoring API

  AR->>RS: gRPC EvaluateSignal
  RS->>OPA: evaluate(signal)
  OPA-->>RS: DENY + deny_reasons
  RS->>OS: gRPC CreateOrderIntent(DENY)
  RS->>MA: risk.events.v1
  Note over OS: no order_intent created
```

Expected outcomes:
1. No broker submission on reject decisions.
2. Reject reason is auditable and visible to operators.

## SD-03: Submit Timeout -> Freeze -> Reconcile
```mermaid
sequenceDiagram
  participant OS as Order Service
  participant IC as Broker Connector
  participant MA as Monitoring API
  participant OP as Operator
  participant IB as IBKR

  OS->>IC: gRPC SubmitOrder
  IC->>IB: placeOrder(order_ref)
  Note over OS,IC: first status missing by 60s deadline
  OS->>OS: state=UNKNOWN_PENDING_RECON
  OS->>MA: system.alerts.v1
  MA-->>OP: trading_mode=FROZEN
  OP->>MA: start reconciliation
  MA->>IC: reconciliation request
  IC->>IB: fetch open orders + positions
  IC-->>MA: reconciliation report
  OP->>MA: acknowledge + resume
  MA->>OS: resume if clean
```

Expected outcomes:
1. New order creation blocked while frozen.
2. Resume requires reconciliation completion and operator acknowledgment.
3. All steps carry actor and trace audit metadata.

## SD-04: Duplicate Callback Handling
```mermaid
sequenceDiagram
  participant IB as IBKR
  participant IC as Broker Connector
  participant OS as Order Service
  participant PS as Performance

  IB-->>IC: execDetails(exec_id=E1)
  IC->>PS: fills.executed.v1(exec_id=E1)
  PS->>PS: apply fill E1

  IB-->>IC: execDetails(exec_id=E1) duplicate
  IC->>PS: fills.executed.v1(exec_id=E1)
  PS->>PS: dedupe no-op

  IB-->>IC: orderStatus(perm_id=P1)
  IC->>OS: orders.status.v1(perm_id=P1)
  IB-->>IC: orderStatus(perm_id=P1) duplicate
  IC->>OS: orders.status.v1(perm_id=P1)
  OS->>OS: idempotent no-op transition
```

Expected outcomes:
1. Exactly-once effective position updates.
2. Duplicate status/fill callbacks cannot create state drift.

## Validation Checklist
Use this checklist when reviewing implementations:
1. Sequence messages map to documented topics/endpoints.
2. Idempotency and dedupe keys are present at every boundary.
3. Timeout and freeze behavior matches `60s` rule.
4. Reconciliation + resume requires explicit operator action.
5. Test evidence exists for all 4 sequences.
