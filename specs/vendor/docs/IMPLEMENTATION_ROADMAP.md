# Implementation Roadmap

For milestone gates and evidence-based deliverable tracking, see
[Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md).

## Blitz Path (4-Day Paper Vertical Slice)
- Day 1: platform bootstrap (`compose`, env templates, CI skeleton).
- Day 2: reliability core (`common-envelope`, `idempotency`, `outbox-inbox`) and DB migrations.
- Day 3: gRPC command path and timeout/freeze safety checks.
- Day 4: reliability drills, evidence pack, and checkpoint tag `impl-v0.1.0-paper-slice`.
- Execution playbook: [4-Day Solo + AI DevOps Blitz Playbook](./DEVOPS_BLITZ_4_DAY_PLAYBOOK.md).

## Phase 1: Foundation
- Project skeleton and service contracts.
- DB schema + flyway migrations.
- Kafka topic bootstrap.
- Basic agent -> risk -> order flow in paper mode.

## Phase 2: Consistency Hardening
- Order state machine and legal transition checks.
- 60s deadline timeout watchdog.
- Reconciliation workflow and freeze gates.

## Phase 3: Dynamic Policies
- OPA sidecar integration.
- Policy bundle CI/CD + rollback.
- Runtime policy decision logging.

## Phase 4: Multi-Instrument Support
- Stock whitelist support.
- MNQ front-month resolver and rollover guard.
- Instrument-aware risk and PnL calculations.

## Phase 5: Operations and Reporting
- Dashboard and operator controls.
- Alerting and runbooks.
- Plan-sync automation for task governance.
