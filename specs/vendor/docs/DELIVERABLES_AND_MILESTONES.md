# Deliverables and Milestones Plan

This document is the execution tracker for planning and delivery.
Use it as the step-by-step review and signoff baseline across teams.

## Planning Rules
1. Every milestone has clear entry/exit criteria.
2. Every deliverable has one primary owner and one reviewer team.
3. No milestone closes without evidence artifacts.
4. Scope changes must update this file and linked task tracker.

Canonical ownership matrix:
[Team Roles and Responsibilities (Canonical)](./TEAM_ROLES_AND_RESPONSIBILITIES.md)

## Baseline Freeze Record (`H0`)
| Field | Value |
|---|---|
| Freeze scope | `M0 + M1` |
| Baseline tag | `spec-v1.0.0-m0m1` |
| Distribution model | pinned git tag in consumer repos |
| Agent assignment model | stream-based teams |
| Service mesh scope | excluded for this milestone |

Reference: [Spec Baseline Tagging and Multi-Agent Handoff](./SPEC_BASELINE_HANDOFF.md)

## Milestone Timeline (Planning Baseline)
| Milestone | Window | Objective | Primary Teams | Exit Gate |
|---|---|---|---|---|
| `M0` | Week 1 | Architecture and contract freeze | Trading Core, API/UI, Data Platform, Policy Platform, SRE | Design signoff complete |
| `M1` | Week 1-2 | Local dev foundation + simulator + validation gates | Platform DevEx, API/UI, QA/Release | Any dev can run local checks |
| `M2` | Week 2-3 | Service skeletons + schema + topics | Trading Core, Data Platform, Broker Connectivity | Services boot + migrations pass |
| `M3` | Week 3-4 | Signal -> risk -> intent path | Trading Core, Policy Platform | Idempotent intent creation verified |
| `M4` | Week 4-5 | Connector submit + callback normalization | Broker Connectivity, Trading Core | No duplicate submit/callback effects |
| `M5` | Week 5-6 | Timeout/freeze/reconciliation enforcement | Trading Core, Broker Connectivity, SRE | 60s timeout path tested end-to-end |
| `M6` | Week 6-7 | Monitoring API + dashboard + SSE | API/UI, Data Platform | Operator controls and live views verified |
| `M7` | Week 7-8 | OPA dynamic policy operations | Policy Platform, Trading Core | Runtime policy updates with audit trail |
| `M8` | Week 8-10 | Paper trading certification and release gates | QA/Release, SRE, All Teams | 10-day paper acceptance criteria met |
| `M9` | Week 10+ | Controlled production rollout | SRE, Trading Core, Program Lead | Go-live checklist complete |

## 4-Day Blitz Timeline (Solo + AI Execution Overlay)
| Blitz Milestone | Day | Objective | Primary Owners | Exit Gate |
|---|---|---|---|---|
| `B1` | Day 1 | Compose + CI + skeleton | Human + Stream D | local stack healthy, CI baseline green |
| `B2` | Day 2 | Idempotency + outbox/inbox | Stream B + Stream C | duplicate suppression and post-commit publish validated |
| `B3` | Day 3 | gRPC command path + freeze controls | Stream A + Stream D | BUY sim flow complete, timeout->freeze verified |
| `B4` | Day 4 | Reliability drills + evidence bundle | Human + all streams | evidence pack complete, reproducible demo |

## Cross-Repo Handoff Milestones
| Milestone | Objective | Exit Gate |
|---|---|---|
| `H0` | Freeze and tag spec baseline (`M0+M1`) | annotated tag pushed |
| `H1` | Sync and verify pinned baseline in code repos | drift checks pass |
| `H2` | Stream kickoff with tagged scope | epics/issues reference pinned tag |
| `H3` | First implementation sprint complete | contract validation evidence attached |
| `H4` | Controlled spec bump cycle | spec-change PR + new tag + consumer bump PR |

## Milestone Deliverables

## `M0` Design and Contract Freeze
| Deliverable ID | Deliverable | Owner | Reviewer | Evidence |
|---|---|---|---|---|
| `D-M0-01` | Current architecture baseline approved | Program Lead | All Teams | Signed review notes |
| `D-M0-02` | API contract baseline (human + OpenAPI) | API/UI | Trading Core | Contract diff + validator pass |
| `D-M0-03` | Kafka topic and key policy baseline | Data Platform | Trading Core | Topic matrix and key rationale |
| `D-M0-04` | Consistency invariants finalized | Trading Core | SRE | Invariant checklist |

## `M1` Local Development Foundation
| Deliverable ID | Deliverable | Owner | Reviewer | Evidence |
|---|---|---|---|---|
| `D-M1-01` | Docker-first local workflow documented | Platform DevEx | QA/Release | Runbook and command logs |
| `D-M1-02` | IBKR simulator and smoke tests available | Broker Connectivity | Trading Core | Smoke test output |
| `D-M1-03` | Contract/build checks runnable locally | Platform DevEx | API/UI | Check logs + docs update |

## `M2` Service and Data Skeletons
| Deliverable ID | Deliverable | Owner | Reviewer | Evidence |
|---|---|---|---|---|
| `D-M2-01` | Service skeletons with health endpoints | Trading Core | SRE | Endpoint screenshots/logs |
| `D-M2-02` | PostgreSQL schema migrations ready | Data Platform | Trading Core | Migration run logs |
| `D-M2-03` | Topic bootstrap and environment config | Data Platform | Broker Connectivity | Bootstrap script logs |

## `M3` to `M5` Core Execution and Consistency
| Deliverable ID | Deliverable | Owner | Reviewer | Evidence |
|---|---|---|---|---|
| `D-M3-01` | Signal -> risk decision path with OPA fail-closed | Policy Platform + Trading Core | QA/Release | Integration test report |
| `D-M4-01` | Order submit/cancel with callback dedupe | Broker Connectivity | Trading Core | Duplicate-callback test |
| `D-M5-01` | 60s timeout -> freeze -> reconcile path | Trading Core | SRE | Game-day run output |
| `D-M5-02` | Reconciliation ack-resume control workflow | API/UI | SRE | Operator flow recording |

## `M6` to `M9` Controls, Operations, Release
| Deliverable ID | Deliverable | Owner | Reviewer | Evidence |
|---|---|---|---|---|
| `D-M6-01` | Monitoring API and dashboard live status | API/UI | SRE | UAT signoff |
| `D-M7-01` | Policy deployment and rollback runbook | Policy Platform | SRE | Rollback drill result |
| `D-M8-01` | 10-day paper-trading acceptance report | QA/Release | Program Lead + SRE | Certification report |
| `D-M9-01` | Controlled go-live and rollback checklist | SRE | All Teams | Go-live review record |

## Blitz Deliverables and Responsibilities
| Deliverable ID | Deliverable | Team / Stream | Reviewer | Evidence |
|---|---|---|---|---|
| `D-B1-01` | Compose baseline runtime (`postgres`, `kafka`, `ibkr-sim`, slice services) | Platform DevEx (Stream D) | Program Lead | health logs and startup checklist |
| `D-B1-02` | CI minimum gates (unit, migration, smoke) | Platform DevEx (Stream D) | QA/Release | CI run links |
| `D-B2-01` | `idempotency_records` behavior in ingress/order paths | Trading Core + Data Platform (Stream B) | Trading Core | duplicate-key test report |
| `D-B2-02` | `outbox_events` and `consumer_inbox` workers | Data Platform (Stream C) | Data Platform | dispatch and dedupe test logs |
| `D-B3-01` | gRPC command chain to broker simulator | Trading Core + API/UI (Stream A) | Trading Core | BUY flow trace with idempotency key |
| `D-B3-02` | timeout->unknown->freeze enforcement | Trading Core + API/UI (Stream A) | SRE | deterministic timeout scenario output |
| `D-B4-01` | Reliability drill suite (outage/restart/replay) | SRE + Platform DevEx (Stream D) | QA/Release | drill run artifacts |
| `D-B4-02` | Certification evidence bundle + checkpoint tag | Program Lead | All Teams | `reports/blitz/*` + `impl-v0.1.0-paper-slice` |

## Cross-Team Review Gates
| Gate | Trigger | Required Reviewers | Pass Condition |
|---|---|---|---|
| `G1-Contracts` | API/topic/schema change | API/UI + Data Platform + Trading Core | Contract validator passes + reviewers approve |
| `G2-Consistency` | Order flow/state logic change | Trading Core + Broker Connectivity + SRE | Timeout/freeze/recon tests pass |
| `G3-Operations` | Control/alerts/runbook changes | SRE + QA/Release + Platform DevEx | Alert drill and runbook walkthrough complete |
| `G4-Release` | Paper->live promotion | QA/Release + SRE + Program Lead | All release gates green |

## Status Model for Tracking
| Status | Meaning |
|---|---|
| `NOT_STARTED` | Planned but not picked up |
| `IN_PROGRESS` | Active work with owner |
| `BLOCKED` | Waiting on dependency/decision |
| `IN_REVIEW` | Implementation complete, under review |
| `DONE` | Accepted with evidence attached |

## Required Evidence Checklist
1. Linked task IDs and PR links.
2. Contract diff (if API/topic/schema changed).
3. Test output or drill evidence.
4. Runbook update link (if operational behavior changed).
5. Approval record from required reviewer teams.

## Dependencies and Risk Flags
| Risk Flag | Description | Owner |
|---|---|---|
| `R-EXT-IBKR` | Broker API behavior mismatch from simulator/paper/live | Broker Connectivity |
| `R-CONS-STATE` | Unknown order state not reconciled before resume | Trading Core |
| `R-DATA-DRIFT` | Position/PnL drift under duplicate/replay scenarios | Data Platform |
| `R-POLICY-OUTAGE` | OPA unavailable causing broad reject behavior | Policy Platform |
| `R-OPS-READINESS` | Missing runbook/alert confidence before release | SRE |

## Mapping to Team Docs
- Trading Core: `docs/design/teams/trading-core.md`
- Broker Connectivity: `docs/design/teams/broker-connectivity.md`
- Policy Platform: `docs/design/teams/policy-platform.md`
- Data Platform: `docs/design/teams/data-platform.md`
- API/UI: `docs/design/teams/api-ui.md`
- SRE: `docs/design/teams/sre.md`
- QA/Release: `docs/design/teams/qa-release.md`
- Platform DevEx: `docs/design/teams/platform-devex.md`
