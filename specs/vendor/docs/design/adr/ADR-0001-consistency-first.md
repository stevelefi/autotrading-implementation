# ADR-0001: Consistency-First Trading Model

## Status
Accepted

## Decision
Prefer strict consistency and safety over availability in uncertain execution states.

## Consequences
- Trading may pause during uncertainty.
- Operator-driven reconciliation becomes mandatory.
