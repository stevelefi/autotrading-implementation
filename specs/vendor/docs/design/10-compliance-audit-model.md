# 10 Compliance and Audit Model

## Auditable Events
- Signal acceptance/rejection
- Policy decision (with version)
- Order submission and broker callback mapping
- Trading mode changes
- Kill switch toggles
- Reconciliation reports and acknowledgments

## Audit Record Minimum Fields
- actor
- action
- resource id
- before/after state
- timestamp UTC
- trace_id

## Retention
- Keep order and reconciliation audit for defined policy period.
- Keep operational logs aligned with retention policy and legal requirements.
