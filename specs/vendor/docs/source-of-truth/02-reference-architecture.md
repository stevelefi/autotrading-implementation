# 02 Reference Architecture

## System Diagram (High Level)
```mermaid
flowchart TB
  subgraph EDGE["Ingress and Operator Edge"]
    U["Operator"]
    EXT["External Systems"]
    UI["dashboard-ui"]
    IG["ingress-gateway-service"]
    MA["monitoring-api"]
  end

  subgraph CORE["Trading Core"]
    EP["event-processor-service"]
    AR["agent-runtime-service"]
    RS["risk-service"]
    OPA["opa-sidecar"]
    PB["policy bundle registry"]
    OS["order-service"]
    IC["ibkr-connector-service"]
    IB["IBKR Gateway/API"]
  end

  subgraph PLATFORM["Discovery and Runtime Config"]
    KD[(Kubernetes Service + DNS)]
    KC[(ConfigMap + Secret)]
  end

  subgraph DATA["State and Event Plane"]
    K[(Kafka)]
    PG[(PostgreSQL)]
    PS["performance-service"]
  end

  U -->|"controls"| MA
  EXT --> IG
  UI --> IG
  UI <-->|"REST/SSE"| MA

  IG -->|"ingress.events.normalized.v1"| K
  K -->|"ingress.events.normalized.v1"| EP
  EP -->|"trade.events.routed.v1"| K
  K -->|"trade.events.routed.v1"| AR

  AR -->|"gRPC EvaluateSignal"| RS
  RS -->|"policy evaluate"| OPA
  OPA -->|"bundle pull"| PB
  RS -->|"gRPC CreateOrderIntent"| OS
  RS -->|"policy.evaluations.audit.v1"| K
  OS -->|"gRPC Submit/Cancel/Replace"| IC
  IC -->|"place/cancel"| IB

  IB -->|"status/fill callbacks"| IC
  IC -->|"orders.status.v1"| K
  K -->|"orders.status.v1"| OS
  IC -->|"fills.executed.v1"| K
  K -->|"fills.executed.v1"| PS
  PS -->|"positions.updated.v1 / pnl.snapshots.v1"| K
  K -->|"projection consume"| MA
  RS -->|"risk.events.v1"| K
  OS -->|"system.alerts.v1"| K

  IG --> PG
  AR --> PG
  RS --> PG
  OS --> PG
  IC --> PG
  PS --> PG
  MA --> PG

  AR -->|"service discovery"| KD
  RS -->|"service discovery"| KD
  OS -->|"service discovery"| KD
  IC -->|"service discovery"| KD
  MA -->|"service discovery"| KD
  AR -->|"runtime config"| KC
  RS -->|"runtime config"| KC
  OS -->|"runtime config"| KC
  IC -->|"runtime config"| KC
```

## Safety Control Loop
```mermaid
flowchart LR
  O["order-service"] -->|"gRPC submit intent"| C["ibkr-connector-service"]
  C -->|"send order"| B["IBKR"]
  B -->|"status <= 60s"| C
  C -->|"orders.status.v1"| O

  O -->|"status timeout"| U["UNKNOWN_PENDING_RECON"]
  U -->|"set FROZEN + alert"| M["monitoring-api"]
  M -->|"operator starts reconciliation"| R["reconciliation run"]
  R -->|"clean + ack"| N["resume NORMAL"]
  R -->|"mismatch"| F["remain FROZEN"]
```

## Architectural Boundaries
- Ingress is centralized in `ingress-gateway-service` and remains Kafka-backed.
- Broker connectivity is isolated to `ibkr-connector-service`.
- `agent-runtime-service`, `risk-service`, `order-service`, and `ibkr-connector-service` use gRPC for buy/sell command path.
- OPA sidecar per `risk-service` pod is authoritative for pre-trade allow/deny.
- Policy-path evaluation stays local to pod and does not use serverless/remote policy calls in hot path.
- Kafka remains the event backbone for ingress, broker status/fill events, and monitoring projections.
- Kubernetes `Service`/DNS provides service discovery and `ConfigMap`/`Secret` provides runtime config.
- Postgres is source of truth for lifecycle state.
- Control actions enter through `monitoring-api` and must be audited.

## Deployment Profile (Initial and Target)
- Planning baseline for local, paper, and production environments.
- Single active connector writer.
- Kafka replication strategy evolves by phase.
- Kubernetes runtime profile evolves by phase (`paper` namespace baseline to production multi-zone cluster profile).
- Postgres backup and recovery strategy required before live promotion.

## Runtime Modes
- `NORMAL`: trading open under policy controls.
- `FROZEN`: new opening orders blocked.
- `KILL_SWITCH`: all new order intents blocked globally.

## Best-Practice Controls (Normative)
1. Order command path MUST be idempotent across gRPC retries and restarts.
2. Missing first status within 60 seconds MUST trigger unknown+freeze path.
3. Resume from freeze MUST require reconciliation + explicit operator ack.
4. OPA evaluation failures for opening orders MUST fail closed.
5. Discovery/config degradation MUST not allow unsafe best-effort trading operations.
6. Clock discipline MUST enforce UTC timestamps and bounded drift.
7. Broker connector MUST maintain single active writer semantics.
8. Ingress events MUST be durably persisted before routed publish.

## Related References
- [Trading Architecture](../TRADING_ARCHITECTURE.md)
- [Rule Engine (OPA)](../RULE_ENGINE_OPA.md)
- [Policy Bundle Contract](../contracts/policy-bundle-contract.md)
- [Policy Decision Audit Contract](../contracts/policy-decision-audit-contract.md)
- [Service Discovery and Config Contract](../contracts/service-discovery-and-config.md)
- [Internal Command Plane Proto](../contracts/protos/internal-command-plane.proto)
- [Order Consistency and Reconciliation](../ORDER_CONSISTENCY_AND_RECONCILIATION.md)
- [Trading Best Practices Baseline](../TRADING_BEST_PRACTICES_BASELINE.md)
