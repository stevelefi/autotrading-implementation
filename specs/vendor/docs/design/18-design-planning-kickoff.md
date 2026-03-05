# 18 Design Planning Kickoff (March 2026)

## Objective
Start a structured design phase that converts architecture goals into approved service designs, locked contracts, and implementation-ready work packages.

## Planning Scope
- Trading critical path: signal -> risk -> order -> broker -> fills -> positions/PnL.
- Control and recovery path: freeze, reconciliation, operator resume controls.
- Platform foundations: schema, event contracts, observability, security, and release gates.

## Planning Principles
1. Contract-first design for REST, Kafka, and database interfaces.
2. Consistency-first behavior under uncertainty (freeze + reconcile, never guess).
3. Explicit ownership and signoff for every component boundary.
4. Design artifacts must include test and rollback strategy before implementation starts.

## Workstreams and Deliverables

| Workstream | Components | Required Design Artifacts | Owner Teams | Signoff Needed |
|---|---|---|---|---|
| WS1: Domain and State Model | `agent-runtime-service`, `risk-service`, `order-service` | State-machine transitions, invariants, timeout semantics, reconciliation triggers | Trading Core | Trading Core + SRE |
| WS2: Broker Connectivity | `ibkr-connector-service`, IBKR integration boundary | Lease/fencing model, callback normalization, dedupe keys (`perm_id`, `exec_id`), disconnect recovery | Broker Connectivity | Broker Connectivity + Trading Core |
| WS3: Contracts and Compatibility | All services | API schemas, topic payload schemas, versioning/migration rules, backward compatibility table | Data Platform + API/UI | Data Platform + QA/Release |
| WS4: Data and Consistency | Postgres + Kafka + outbox/inbox | Table/index design, transaction boundaries, replay/backfill plan, data retention plan | Data Platform | Data Platform + SRE |
| WS5: Policy and Governance | `risk-service`, OPA sidecar, policy bundles | Policy input/output schema, fail-closed behavior, rollout/rollback plan | Policy Platform | Policy Platform + Risk/Compliance |
| WS6: Operator Experience and Ingress | `ingress-gateway-service`, `monitoring-api`, `dashboard-ui` | Ingress protocol UX/contract behavior, operator workflows, SSE model, alert UX, reconciliation run UX | API/UI | API/UI + Ops |
| WS7: Reliability and Release | Cross-service | SLO/alert mapping, game-day matrix, release and rollback checklists | SRE + QA/Release | SRE + QA/Release + Program Leads |

## 6-Week Design Schedule

| Week | Dates (America/Los_Angeles) | Focus | Exit Criteria |
|---|---|---|---|
| Week 1 | March 4-10, 2026 | Finalize scope, owners, and boundary contracts | Workstream ownership confirmed; design-review calendar published |
| Week 2 | March 11-17, 2026 | State models and sequence flows | Core and failure-path sequences approved by Trading Core/Broker/SRE |
| Week 3 | March 18-24, 2026 | API + Kafka schema lock (`v1`) | Contract set reviewed with compatibility notes and test strategy |
| Week 4 | March 25-31, 2026 | Data model and consistency hardening design | DB schema/index and outbox/inbox behavior signed off |
| Week 5 | April 1-7, 2026 | Control plane, observability, and incident design | Freeze/reconcile/runbook flow validated end-to-end on paper |
| Week 6 | April 8-14, 2026 | Final architecture review and implementation handoff | Go/no-go decision for implementation sprint backlog |

## Design Review Workflow
1. Draft design using [Design Review Template](./templates/design-review-template.md).
2. Map proposed behavior to service contracts (`docs/contracts/*.md`).
3. Record boundary changes as ADR updates (`docs/design/adr/*`).
4. Add acceptance tests and rollback strategy before approval.
5. Merge only after required signoffs in this plan are complete.

## Initial ADR Queue for This Planning Cycle
- Reconciliation orchestration authority (which service coordinates run lifecycle).
- Topic partitioning strategy beyond `agent_id` for scale-out.
- Timeout rule extension (instrument-specific versus fixed 60 seconds).
- Operator override model and audit requirements for manual resumes.
- Multi-instance connector failover safety checks.

## Immediate Actions (Next 5 Business Days)
1. Assign one DRI per workstream and publish in `docs/COMPONENTS_AND_OWNERSHIP.md`.
2. Open one design-review issue/PR per workstream using the template.
3. Resolve open questions with target dates in `16-open-questions-and-assumptions.md`.
4. Define signoff attendees and weekly design review cadence.
5. Freeze net-new scope until Week 2 signoff completes.

## Definition of Done for Design Phase
- All workstreams have approved design docs with signoffs.
- All API/topic/schema changes are versioned and backward compatibility is documented.
- Failure and rollback paths are documented and traceable to runbooks.
- Implementation backlog is mapped to accepted design artifacts and owners.
