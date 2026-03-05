# 12 Release Rollout Strategy

## Stages
1. Local and integration tests.
2. Paper environment deploy.
3. Soak tests and drills.
4. Limited live rollout window.
5. Gradual exposure increase.

## Rollback Strategy
- Keep trading mode frozen while rolling back if uncertainty exists.
- Rollback code image and policy bundle independently.
- Reconciliation required before returning to normal.

## Go/No-Go Checklist
- P0 alerts healthy.
- No unresolved reconciliation mismatches.
- Policy bundle verified.
- Connector lease and health confirmed.
