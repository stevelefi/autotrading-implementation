# Trading Best Practices Baseline

This document is the production-readiness baseline for the trading platform.
Use it as the review checklist across architecture, implementation, and release gates.

## How To Use This Baseline
1. Treat each control as `MUST` unless an exception is approved and documented.
2. Link implementation tasks and test evidence to each control.
3. Re-run this checklist at each milestone (`M0` through go-live).

## 1) Execution Correctness
1. A single intent must map to one immutable `order_intent_id`.
2. Signal and intent creation must require `idempotency_key`.
3. Fills must dedupe by `exec_id`; broker orders by `perm_id`/`order_ref`.
4. Unknown broker state must block new opening orders immediately.
5. Submission status must be observed within 60 seconds or transition to `UNKNOWN_PENDING_RECON`.
6. Resume from frozen mode must require reconciliation completion and explicit operator acknowledgement.
7. Manual and external trade-event ingress must persist raw payloads and canonical route lineage before agent processing.

Primary references:
- [Trading Architecture](./TRADING_ARCHITECTURE.md)
- [Order Consistency and Reconciliation](./ORDER_CONSISTENCY_AND_RECONCILIATION.md)
- [Database Schema](./DATABASE_SCHEMA.md)

## 2) Risk and Policy Governance
1. Pre-trade checks must include instrument allowlist, quantity limits, and daily risk limits.
2. Policy evaluation must fail closed if OPA is unavailable.
3. Policy bundles must be versioned and auditable.
4. Policy changes must have rollback path and operator visibility.

Primary references:
- [Rule Engine (OPA)](./RULE_ENGINE_OPA.md)
- [Risk Service Contract](./contracts/risk-service.md)

## 3) Broker Connectivity and Session Safety
1. Broker integration must have single active writer semantics.
2. Connector startup must perform reconciliation against broker open orders/positions.
3. Disconnect/reconnect behavior must be deterministic and tested.
4. Trading session controls must prevent orders outside allowed windows unless explicitly configured.

Primary references:
- [IBKR Connector Contract](./contracts/ibkr-connector-service.md)
- [Deployment and Environments](./DEPLOYMENT_AND_ENVIRONMENTS.md)
- [Trading Ops Runbook](./runbooks/trading-ops.md)

## 4) Data Integrity and Recovery
1. Postgres is authoritative for lifecycle state and control mode.
2. Outbox/inbox patterns must be used for reliable publish/consume.
3. All lifecycle transitions must be persisted in history tables.
4. Reconciliation runs must persist mismatch details and ack actors.
5. High-volume tables should have partitioning and retention strategy.

Primary references:
- [Database Schema](./DATABASE_SCHEMA.md)
- [Kafka Event Contracts](./KAFKA_EVENT_CONTRACTS.md)
- [Source of Truth: Domain and Data Contracts](./source-of-truth/03-domain-model-data-contracts.md)

## 5) Operational Controls and Security
1. Kill switch and trading mode must be authenticated, authorized, and audited.
2. Secrets must be externally managed and rotated.
3. Internal service access should follow least privilege and network allowlisting.
4. Critical control changes must include actor identity, trace, and timestamp.
5. Freeze/reconcile/resume flow must be covered by runbooks and drills.

Primary references:
- [Security and Compliance](./SECURITY_AND_COMPLIANCE.md)
- [Observability and Alerting](./OBSERVABILITY_AND_ALERTING.md)
- [Source of Truth: Ops, Security, Reliability](./source-of-truth/05-operations-security-reliability.md)

## 6) Observability and SLO Management
1. Dashboards must track order latency, callback timeout rate, drift, lag, and policy availability.
2. P0 alerts must include unknown state, split-brain risk, and control-plane failures.
3. Alerts must map to runbooks with clear on-call actions.
4. Trace correlation must include `trace_id`, `agent_id`, and `order_intent_id`.

Primary references:
- [Observability and Alerting](./OBSERVABILITY_AND_ALERTING.md)
- [Cross-Service SLO](./contracts/cross-service-slo.md)

## 7) Testing and Release Discipline
1. Functional, integration, E2E, and chaos tests must pass before production promotion.
2. Mandatory scenarios include duplicates, timeout freeze, late callback, and reconciliation resume.
3. Paper-trading soak gate must pass with zero unexplained position drift.
4. Release progression must stop on any P0 consistency violation.

Primary references:
- [Testing and Release Gates](./TESTING_AND_RELEASE_GATES.md)
- [Implementation Phases](./IMPLEMENTATION_PHASES.md)
- [Deliverables and Milestones](./DELIVERABLES_AND_MILESTONES.md)

## 8) Documentation and Change Governance
1. Architecture, contracts, and runbooks must be versioned in repo.
2. Plan and task updates must be traceable to PRs and release tags.
3. Breaking contract changes must follow versioning and migration policy.
4. Team ownership must be explicit for each service and control area.

Primary references:
- [Source of Truth Specification](./source-of-truth/README.md)
- [Service Contract Catalog](./source-of-truth/04-service-contract-catalog.md)
- [Task Tracking Guide](./TASK_TRACKING_GUIDE.md)

## Cross-Doc Coverage Matrix
| Control Area | Core Doc | Enforcement Doc | Ops/Runbook Doc |
|---|---|---|---|
| Execution correctness | `TRADING_ARCHITECTURE.md` | `DATABASE_SCHEMA.md` | `runbooks/trading-ops.md` |
| Risk policy | `RULE_ENGINE_OPA.md` | `contracts/risk-service.md` | `runbooks/trading-ops.md` |
| Broker safety | `contracts/ibkr-connector-service.md` | `ORDER_CONSISTENCY_AND_RECONCILIATION.md` | `runbooks/trading-ops.md` |
| Data recovery | `DATABASE_SCHEMA.md` | `KAFKA_EVENT_CONTRACTS.md` | `source-of-truth/05-operations-security-reliability.md` |
| Security controls | `SECURITY_AND_COMPLIANCE.md` | `API_SPEC.md` | `runbooks/trading-ops.md` |
| SLO/alerts | `OBSERVABILITY_AND_ALERTING.md` | `contracts/cross-service-slo.md` | `runbooks/trading-ops.md` |
| Release quality | `TESTING_AND_RELEASE_GATES.md` | `IMPLEMENTATION_PHASES.md` | `DELIVERABLES_AND_MILESTONES.md` |
