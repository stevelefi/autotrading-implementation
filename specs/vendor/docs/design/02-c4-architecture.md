# 02 C4 Architecture

## Context View
```mermaid
flowchart LR
  U["Trader/Operator"] --> UI["dashboard-ui"]
  RC["Risk/Compliance Reviewer"] --> UI
  UI --> MA["monitoring-api"]
  UI --> IG["ingress-gateway-service"]
  EXT["External Trading Systems"] --> IG
  IG --> CORE["Trading Core Services"]
  MA --> CORE
  CORE --> IB["IB Gateway / IBKR"]
  CORE --> OPA["OPA + Policy Bundles"]
  CORE --> OBS["Observability Stack"]
  CORE --> GH["GitHub (plan/task governance)"]
```

## Container View
```mermaid
flowchart TB
  subgraph EDGE["Edge"]
    UI["dashboard-ui"]
    MA["monitoring-api"]
    IG["ingress-gateway-service"]
  end

  subgraph CORE["Core Trading Containers"]
    EP["event-processor-service"]
    AR["agent-runtime-service"]
    RS["risk-service"]
    OPA["opa-sidecar"]
    OS["order-service"]
    IC["ibkr-connector-service"]
    PS["performance-service"]
  end

  subgraph STATE["State and Event"]
    PG[(postgres)]
    K[(kafka)]
  end

  UI <-->|"REST/SSE"| MA
  UI -->|"REST/WebSocket ingress"| IG
  IG -->|"ingress.events.normalized.v1"| EP
  EP -->|"trade.events.routed.v1"| AR
  AR -->|"gRPC EvaluateSignal"| RS
  RS -->|"gRPC CreateOrderIntent"| OS
  RS -->|"policy evaluate"| OPA
  OS -->|"gRPC Submit/Cancel/Replace"| IC
  RS -->|"policy.evaluations.audit.v1"| K
  IC -->|"orders.status.v1 + fills.executed.v1"| PS
  PS -->|"projections"| MA

  IG --> PG
  MA --> PG
  AR --> PG
  RS --> PG
  OS --> PG
  IC --> PG
  PS --> PG

  IG --> K
  K --> MA
  AR --> K
  RS --> K
  OS --> K
  IC --> K
  PS --> K
```

## Component View (Ingress and Order Path)
- Ingress protocol adapters (WebHook/API/gRPC/WebSocket)
- Ingress authn/authz and idempotency guard
- Immutable raw-event writer + outbox publisher
- Downstream event processing/routing
- Signal intake
- Policy evaluator
- Order ledger writer
- Broker adapter
- Status/fill projector
- Reconciliation engine

## Boundary Decisions
- Ingress gateway is the single public event-ingress authority.
- Monitoring API remains control/query and does not own new event ingress.
- Risk and policy are separate from order submission.
- Read models are decoupled from order-write ledger.
- Unknown state transitions prioritize safety over availability (freeze first).
