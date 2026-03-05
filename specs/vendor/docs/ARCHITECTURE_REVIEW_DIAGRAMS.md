# Architecture Review Diagrams

This page is the review pack for architecture diagrams.
Each diagram is intentionally high-level and optimized for readability in design reviews.

## Diagram 1: Runtime Overview
```mermaid
flowchart TB
  subgraph EDGE["Ingress and Operator Edge"]
    EXT["External Systems"]
    UI["Trader UI"]
    IG["ingress-gateway-service"]
    MA["monitoring-api"]
  end

  subgraph CORE["Core Trading Services"]
    EP["event-processor-service"]
    AR["agent-runtime-service"]
    RS["risk-service"]
    OS["order-service"]
    IC["ibkr-connector-service"]
    OPA["opa-sidecar"]
  end

  subgraph PROJ["Projection"]
    PS["performance-service"]
  end

  EXT --> IG
  UI --> IG
  UI <-->|"REST/SSE"| MA

  IG --> EP --> AR --> RS --> OS --> IC
  RS --> OPA
  IC -->|"place/cancel"| IB["IBKR"]
  IC --> PS --> MA
```

Team review focus:
- Ingress boundary ownership and scope.
- Core service chain clarity.
- Monitoring path for operators.

## Diagram 2: Event Pipeline and Keys
```mermaid
flowchart LR
  E0["ingress.events.normalized.v1\nkey: agent_id or integration_id\nids: ingress_event_id"] -->
  E1["trade.events.routed.v1\nkey: agent_id\nids: raw_event_id, trade_event_id"] -->
  C2["gRPC EvaluateSignal\nkeys: trace_id,idempotency_key,signal_id"] -->
  C3["gRPC CreateOrderIntent\nkeys: signal_id,idempotency_key"] -->
  C4["gRPC SubmitOrder\nkeys: order_intent_id,idempotency_key"] -->
  E5["orders.status.v1\nkey: agent_id\nids: perm_id, order_intent_id"]

  C4 --> E6["fills.executed.v1\nkey: agent_id\nids: exec_id, order_intent_id"]
  E6 --> E7["positions.updated.v1"]
  E6 --> E8["pnl.snapshots.v1"]
```

Team review focus:
- Transport split clarity (Kafka backbone vs gRPC command path).
- Partition-key consistency.
- Lineage preservation through stages.
- Dedupe identity coverage.

## Diagram 3: Control and Recovery Loop
```mermaid
flowchart LR
  O["Operator"] -->|"control command"| MA["monitoring-api"]
  MA --> OS["order-service"]
  OS -->|"critical timeout alert"| MA
  O -->|"start reconciliation"| MA
  MA --> IC["ibkr-connector-service"]
  IC -->|"fetch broker state"| B["IBKR"]
  IC -->|"reconciliation report"| MA
  O -->|"ack + resume"| MA
  MA -->|"resume allowed"| OS
```

Team review focus:
- Freeze/reconcile/resume safety.
- Operator authority and audit flow.
- Broker comparison feedback path.

## Diagram 4: Team Ownership
```mermaid
flowchart TB
  TC["Trading Core"] --> AR["agent-runtime-service"]
  TC --> OS["order-service"]

  PP["Policy Platform"] --> RS["risk-service"]
  PP --> OPA["opa-sidecar"]

  BC["Broker Connectivity"] --> IC["ibkr-connector-service"]

  DP["Data Platform"] --> PS["performance-service"]
  DP --> DATA["Schema + retention + replay"]

  API["API and UI"] --> IG["ingress-gateway-service"]
  API --> MA["monitoring-api"]
  API --> UI["dashboard-ui"]

  SRE["SRE"] --> OPS["alerts + runbooks + SLOs"]
  QA["QA and Release"] --> GATES["test gates + signoff"]
  DX["Platform DevEx"] --> TOOLS["automation + docs pipeline"]
```

Team review focus:
- Clear primary owner per runtime boundary.
- Cross-team interfaces and handoff points.

## Diagram 5: Consistency Escalation Path
```mermaid
stateDiagram-v2
  [*] --> NORMAL
  NORMAL --> SUBMIT_PENDING : order intent created
  SUBMIT_PENDING --> ACKED : broker status <= 60s
  SUBMIT_PENDING --> UNKNOWN_PENDING_RECON : status timeout
  UNKNOWN_PENDING_RECON --> FROZEN : freeze and alert
  FROZEN --> RECON_RUNNING : operator starts reconciliation
  RECON_RUNNING --> RECON_CLEAN : no unresolved mismatch
  RECON_RUNNING --> RECON_FAILED : mismatch unresolved
  RECON_CLEAN --> NORMAL : operator ack + resume
  RECON_FAILED --> FROZEN : remain frozen
```

Team review focus:
- Legal state transitions.
- Recovery gate behavior.
- Operational escalation path.

## Cross-Team Review Checklist
1. Service boundaries and ownership are explicit.
2. Critical keys and lineage are preserved end-to-end.
3. Recovery path is deterministic and operator-controlled.
4. Design changes identify impacted teams and docs.

## Related Docs
- [Trading Architecture](./TRADING_ARCHITECTURE.md)
- [Production Plan](./PRODUCTION_PLAN.md)
- [Component Interactions and Documentation Plan](./design/17-component-interactions-and-doc-plan.md)
- [Component Contract Matrix](./design/19-component-contract-matrix.md)
