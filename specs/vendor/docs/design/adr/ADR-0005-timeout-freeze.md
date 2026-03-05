# ADR-0005: 60s Submit Timeout Triggers Freeze

## Status
Accepted

## Decision
If no broker status is observed within 60 seconds after submit, mark unknown and freeze opening orders.

## Consequences
- Faster containment of ambiguous execution states.
- Requires reliable reconciliation and operator workflow.
