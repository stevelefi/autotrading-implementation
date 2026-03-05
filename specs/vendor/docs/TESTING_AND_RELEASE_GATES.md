# Testing and Release Gates

## Test Layers
- Unit: state transitions, policy evaluations, validation rules.
- Integration: DB migrations, outbox/inbox, Kafka contracts.
- E2E: signal -> risk -> order -> callback -> position/pnl.
- Chaos: disconnect/reconnect, delayed callbacks, kafka outage.
- Replay: historical event stream replay consistency checks.

## Mandatory Scenarios
1. Duplicate idempotency key ignored.
2. No broker status in 60s => unknown + freeze.
3. Late callback after timeout handled without duplicate submit.
4. Reconciliation clears mismatch and supports resume.
5. MNQ rollover to next front-month contract.
6. OPA unavailable => fail-closed for opening orders.
7. OPA timeout => fail-closed deny with reason code `OPA_TIMEOUT`.
8. OPA schema mismatch => fail-closed deny with reason code `OPA_SCHEMA_ERROR`.
9. Invalid policy bundle signature blocks activation.
10. Bundle rollback restores previous decision behavior.
11. Connector restart during active session preserves lifecycle correctness.
12. Outbox backlog drains without duplicate state transitions.
13. Clock-skew detection path validates UTC and safety behavior.

## Release Gates
- Gate 1: migrations and contract checks pass.
- Gate 2: policy governance and bundle checks pass.
- Gate 3: critical consistency scenarios pass.
- Gate 4: observability alerts verified.
- Gate 5: 10-day paper soak with zero unexplained drift.
- Gate 6: production go-live checklist signoff.

## Evidence Requirements By Gate
1. Gate 1: CI artifacts for migrations, schema checks, and contract validation.
2. Gate 2: bundle signature verification, approval evidence, rollback rehearsal logs.
3. Gate 3: automated test report for mandatory scenarios.
4. Gate 4: alert drill evidence and runbook validation records.
5. Gate 5: paper-trading drift report and incident summary.
6. Gate 6: signed approval from Trading Core, Broker, Policy Platform, SRE, QA/Release.

## Promotion Rules
1. Any P0 consistency failure blocks promotion.
2. Any unresolved reconciliation mismatch blocks promotion.
3. Any missing test evidence blocks promotion.
4. Production rollout must start with controlled exposure and rollback readiness.

## Related References
- [Implementation Phases and Team Plan](./IMPLEMENTATION_PHASES.md)
- [Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md)
- [Trading Best Practices Baseline](./TRADING_BEST_PRACTICES_BASELINE.md)
