# Team Roles and Responsibilities (Canonical)

This document is the single source of truth for team-level ownership, responsibilities, and escalation boundaries.

## Canonical Team Vocabulary
Use these names consistently across docs, tasks, and issue labels:
1. Trading Core
2. Broker Connectivity
3. Policy Platform
4. Data Platform
5. API/UI
6. SRE
7. QA/Release
8. Platform DevEx
9. Program Lead (role; cross-team governance and final gate)

## Role Schema
All ownership matrices in this repository use these columns:
- `Team`
- `Primary Mission`
- `Accountable Decisions`
- `Responsible Activities`
- `Consulted Teams`
- `Informed Teams`
- `Owned Services`
- `Owned Repos`
- `Primary Artifacts`
- `Entry/Exit Criteria`
- `Escalation Triggers`

## Functional Team Matrix (8 Teams)
| Team | Primary Mission | Accountable Decisions | Responsible Activities | Consulted Teams | Informed Teams | Owned Services | Owned Repos | Primary Artifacts | Entry/Exit Criteria | Escalation Triggers |
|---|---|---|---|---|---|---|---|---|---|---|
| Trading Core | Deterministic command and lifecycle behavior | Order state machine transitions, timeout/freeze policy, command-path idempotency | Signal routing, risk/order orchestration, timeout watchdog, reconcile hooks | Broker Connectivity, Policy Platform, Data Platform | API/UI, SRE, QA/Release | `event-processor-service`, `agent-runtime-service`, `risk-service`, `order-service` | `autotrading-implementation` | state machine spec, command contract tests, timeout/freeze test evidence | Entry: M2 contracts locked. Exit: M5 consistency scenarios pass. | duplicate intent risk, unknown state without freeze, gRPC idempotency regressions |
| Broker Connectivity | Reliable broker command/callback integration | Single active connector writer, callback normalization rules | IBKR connector logic, callback dedupe, reconnect/reconcile support | Trading Core, SRE | Data Platform, QA/Release | `ibkr-connector-service` | `autotrading-implementation` | connector runbooks, callback mapping specs, broker integration tests | Entry: connector contracts approved. Exit: no duplicate submit/callback effects. | reconnect failure, callback lag/duplication, broker API behavior drift |
| Policy Platform | Safe and auditable runtime policy control | OPA bundle promotion, policy version governance, fail-closed behavior | Rego policy authoring, bundle signing, policy CI gates, explainability taxonomy | Trading Core, SRE | API/UI, QA/Release | OPA sidecar policy path for `risk-service` | `autotrading-policy`, `autotrading` | policy bundle contracts, audit contract, rollback evidence | Entry: policy schema locked. Exit: promotion+rollback drills pass. | OPA unavailable, invalid bundle activation, schema mismatch denies |
| Data Platform | Durable state and event consistency | DB schema evolution, dedupe keys, outbox/inbox integrity | migrations, replay support, query/perf patterns, event governance | Trading Core, SRE | API/UI, QA/Release | persistence and event-plane support across all services | `autotrading-implementation` | migration plans, replay drills, topic/key policy docs | Entry: schema baseline accepted. Exit: replay/restore checks pass. | migration risk, data drift, outbox backlog saturation |
| API/UI | Operator visibility and control safety | ingress/API contract behavior, operator workflow gates | ingress boundary APIs, monitoring APIs, dashboard/SSE behavior | Trading Core, Policy Platform | SRE, QA/Release | `ingress-gateway-service`, `monitoring-api`, `dashboard-ui` | `autotrading-implementation` | API contracts, UX workflows, operator action audit flows | Entry: contract catalog approved. Exit: UAT operator flows pass. | hidden frozen/unknown state, unsafe mutating control path |
| SRE | Runtime reliability and incident response | alert policy thresholds, runtime fail-closed posture, rollback execution | observability, deployment safety, drill execution, release ops controls | Trading Core, Broker Connectivity, Platform DevEx | QA/Release, Program Lead | runtime health/ops controls for all services | `autotrading-devops` | runbooks, dashboards, drill logs, rollout/rollback checklists | Entry: baseline SLO and alerts defined. Exit: incident drills pass. | unresolved P0/P1 alerts, rollback failure, degraded runtime dependencies |
| QA/Release | Release quality and evidence governance | release gate pass/fail decisions, evidence completeness | E2E/chaos/soak validation, certification reporting, gate checks | SRE, Trading Core, API/UI | Program Lead | cross-service test/release controls | `autotrading-devops`, `autotrading-implementation` | test reports, soak evidence, release signoff artifacts | Entry: test matrix approved. Exit: gate evidence complete and reviewed. | unresolved P0 defects, missing evidence on DONE deliverables |
| Platform DevEx | Delivery tooling and planning automation | CI policy enforcement, spec-sync governance, docs automation reliability | workflow automation, task sync tooling, docs build/release guardrails | SRE, QA/Release | All Teams | planning/tooling controls in spec repo | `autotrading`, `autotrading-devops` | plan-sync tooling, validation workflows, governance docs | Entry: tooling requirements agreed. Exit: automation pipelines stable. | CI drift, spec pinning drift, task governance breakdown |

## Stream Overlay Matrix (Execution)
| Stream | Primary Teams | Human Gate | Mission | Entry Criteria | Exit Criteria | Handoff Outputs | Escalate To |
|---|---|---|---|---|---|---|---|
| Stream A | Trading Core + API/UI | Program Lead | Service contracts and envelope propagation | contracts and envelope fields locked | envelope propagation verified across path | contract test evidence and interface notes | Program Lead |
| Stream B | Trading Core + Data Platform | Program Lead | Idempotency and migrations | dedupe model and table design approved | replay/conflict semantics verified | idempotency behavior evidence | Program Lead |
| Stream C | Data Platform + Broker Connectivity | Program Lead | Outbox/inbox workers and consumer dedupe | transactional boundaries approved | no duplicate side effects on replay/restart | worker reliability evidence | Program Lead |
| Stream D | Platform DevEx + SRE + QA/Release | Program Lead | Compose, Helm, CI/e2e, evidence packaging | baseline repo and runtime contracts available | validation gates and evidence pack complete | release readiness artifacts | Program Lead |

## Repo Ownership Matrix
| Repo | Accountable Team | Responsible Teams | Primary Purpose | Change Control Gate | Required Checks |
|---|---|---|---|---|---|
| `autotrading` | Platform DevEx | Platform DevEx + Program Lead | architecture/spec/contracts/docs source of truth | spec PR approval + tagged baseline | `mkdocs build --strict`, plan-sync validate/sync |
| `autotrading-implementation` | Trading Core | Trading Core + Broker Connectivity + Data Platform + API/UI | services, libs, DB, runtime command/data logic | pinned spec version + contract conformance | unit/integration checks + spec verify |
| `autotrading-devops` | SRE | SRE + Platform DevEx + QA/Release | compose/helm/workflows/ops runbooks | release gate + rollback readiness | compose smoke, helm lint/template, gate checks |
| `autotrading-policy` | Policy Platform | Policy Platform + Trading Core + QA/Release | OPA policies, bundle promotion, explainability governance | signed bundle + approval gates | policy lint/tests/regression/perf + schema checks |

## Canonical Naming Migration
Legacy aliases must be replaced with canonical names:

| Legacy Alias | Canonical Name |
|---|---|
| `Platform-DevEx`, `Platform/DevEx` | Platform DevEx |
| `Trading-Core` | Trading Core |
| `Data-Platform` | Data Platform |
| `QA-Release`, `QA` | QA/Release |
| `Broker` | Broker Connectivity |
| `Policy` | Policy Platform |
| `Ops`, `Product/Ops`, `All leads` | SRE or Program Lead or All Teams (context-specific) |

