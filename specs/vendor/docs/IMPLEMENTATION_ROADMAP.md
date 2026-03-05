# Implementation Roadmap

For milestone gates and evidence-based deliverable tracking, see
[Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md).

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
