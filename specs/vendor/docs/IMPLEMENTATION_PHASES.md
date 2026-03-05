# Implementation Phases and Team Plan

This is the execution plan for building and operating the trading platform with strong consistency controls.
Detailed deliverable tracking is maintained in [Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md).

## Baseline Pinning Rules for Implementation Repos
1. This execution cycle starts from frozen scope `M0 + M1`.
2. Baseline tag for implementation kickoff is `spec-v1.0.0-m0m1`.
3. Consumer repos must implement against pinned tag snapshots, not `main`.
4. Service mesh adoption is out of scope for this milestone.
5. Any spec change requires spec-repo PR + new tag + consumer bump PR.

Reference: [Spec Baseline Tagging and Multi-Agent Handoff](./SPEC_BASELINE_HANDOFF.md)

## Phase 0: Design Freeze and Contract Lock (Week 1)

## Objectives
- Freeze service naming and responsibilities.
- Lock API + event + DB contract baselines.
- Define non-negotiable consistency invariants.

## Team Deliverables
- Trading Core: order lifecycle state machine spec (`60s` timeout -> freeze).
- API/UI: endpoint catalog and OpenAPI source.
- Data Platform: unique constraints and dedupe key design.
- Policy Platform: OPA input/output contract + fail-closed behavior.
- SRE: initial SLO and alert taxonomy.

## Exit Criteria
- `docs/current-design-baseline.md` approved.
- OpenAPI and API markdown parity check passing.
- Contract review sign-off by Trading Core + API/UI + SRE.

## Phase 1: Local DEV Foundation (Week 1-2)

## Objectives
- Make all contributors productive without cloud dependency.
- Standardize docs-first local validation.

## Team Deliverables
- Platform/DevEx:
  - docs workflow for contract validation + strict MkDocs builds.
  - plan-sync packaging and validation tooling.
  - local commands documented in `docs/DEV_WORKFLOW_DOCKER.md`.
- API/UI + Docs owners:
  - run local docs preview and fix broken links.
- QA/Release:
  - define required local checks before PR.

## Exit Criteria
- Any developer can run docs and contract checks with one documented local flow.
- Local CI checks complete with no runtime stack dependency.
- Planning artifacts stay synchronized with issue/project tracking.

## Phase 2: Shared Platform and Skeleton Services (Week 2-3)

## Objectives
- Scaffold all services with consistent cross-cutting patterns.

## Team Deliverables
- Trading Core + Data Platform:
  - shared libraries for ids, envelope, trace, idempotency.
  - PostgreSQL migrations for agents/orders/fills/positions.
- Broker Connectivity:
  - connector service skeleton with single-active lease mechanism.
- API/UI:
  - monitoring-api skeleton with auth middleware and error envelope.

## Exit Criteria
- Services boot with health endpoints.
- Flyway migrations applied cleanly in DEV.
- Kafka topic/bootstrap config scripted.

## Phase 3: Core Order Consistency Path (Week 3-5)

## Objectives
- Implement end-to-end path: signal -> risk -> order intent -> submit -> status.
- Enforce hard consistency constraints.

## Team Deliverables
- Trading Core:
  - order-service state transitions + `60s` submit-status deadline.
  - freeze behavior when status missing.
- Broker Connectivity:
  - submit/cancel path to IBKR paper gateway.
  - callback normalization and dedupe (`perm_id`, `exec_id`).
- Data Platform:
  - outbox/inbox processing and retry idempotency.

## Exit Criteria
- Duplicate signals do not create duplicate intents.
- Missing broker status triggers `UNKNOWN_PENDING_RECON` and freeze.
- Reconciliation hooks are callable.

## Phase 4: OPA Dynamic Policy Engine (Week 5-6)

## Objectives
- Externalize risk logic and allow dynamic policy change without redeploy.

## Team Deliverables
- Policy Platform:
  - OPA sidecar integration and bundle versioning.
  - policy pack for time window, instrument guardrails, qty/position limits.
- Trading Core:
  - risk-service policy input normalization.
  - fail-closed on OPA timeout/unavailable.

## Exit Criteria
- Policy changes reflected at runtime with version trace.
- Policy outage leads to controlled rejects and alerts, not unsafe execution.

## Phase 5: Monitoring API and Dashboard (Week 6-7)

## Objectives
- Deliver operator visibility and controls.

## Team Deliverables
- API/UI:
  - implement `monitoring-api` endpoints and SSE stream.
  - dashboard for per-agent orders, positions, PnL, risk events.
- SRE:
  - dashboard health and stale-data indicators.

## Exit Criteria
- API endpoints conform to OpenAPI and contract docs.
- Dashboard freshness under target and control actions audited.

## Phase 6: Reconciliation and Operational Hardening (Week 7-8)

## Objectives
- Make recovery deterministic and operations safe.

## Team Deliverables
- Trading Core + Broker Connectivity:
  - startup reconciliation flow and mismatch detection.
  - operator ack/resume gating.
- SRE:
  - alerting runbooks for timeout/freeze/reconcile.
  - game-day scenarios for disconnect/restart/lag.

## Exit Criteria
- Reconciliation required before resume from uncertain state.
- P0/P1 alert drills pass with documented runbook evidence.

## Phase 7: Paper Trading Certification (Week 8-10)

## Objectives
- Prove production readiness in paper environment.

## Team Deliverables
- QA/Release:
  - execute functional/reliability/performance tests.
  - certify acceptance criteria and traceability.
- All teams:
  - close gaps and document residual risks.

## Exit Criteria
- 10 consecutive paper-trading days with:
  - zero lost order/fill events,
  - zero unexplained position drift,
  - duplicate submission protection verified.

## Phase 8: Controlled Live Rollout (Week 10+)

## Objectives
- Minimize blast radius while enabling live trading.

## Team Deliverables
- SRE + Trading Core:
  - limited rollout window and kill-switch rehearsal.
  - live rollback and freeze drills.
- Product/Ops:
  - go-live approval checklist and reporting cadence.

## Exit Criteria
- staged live activation complete with no unresolved P1 incidents.
- post-launch review complete with prioritized hardening backlog.

## Traceability and Tracking Model
Use GitHub Issues/Projects as source of truth and Excel as reporting artifact.

Required fields per task:
- `Task ID`
- `Epic`
- `Owner Team`
- `Service`
- `Contract Scope` (API/Kafka/DB)
- `Risk Tier` (P0/P1/P2)
- `Acceptance Evidence`
- `Release Tag`

Definition of done per task:
1. Code merged with linked task.
2. Contract/docs updated if behavior changed.
3. Tests pass and evidence attached.
4. Ops impact documented in runbook if applicable.
