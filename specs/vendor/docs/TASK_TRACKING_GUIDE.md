# Task Tracking Guide

## Canonical Sources
- Human plan: `docs/IMPLEMENTATION_PLAN.md`
- Task ledger: `docs/tasks.yaml`
- Validation schema: `docs/tasks.schema.json`

## Workflow
1. Update `docs/tasks.yaml` in PR.
2. `plan-validate` checks schema and transitions.
3. On merge, `plan-sync` updates GitHub issues/projects.
4. `excel-export` publishes reporting workbook.
5. `pm-summary` updates `[PM] Program Status` issue with milestone/risk escalations.

## Required Task Fields
- task_id
- epic
- owner_team
- service
- status
- risk_tier
- acceptance_criteria
- release_tag
- evidence_url (required when DONE)

Optional additive fields:
- milestone
- blocked_by
- target_date

Canonical `owner_team` values:
- Trading Core
- Broker Connectivity
- Policy Platform
- Data Platform
- API/UI
- SRE
- QA/Release
- Platform DevEx
- Program Lead

Milestone convention for blitz execution:
- `B1`, `B2`, `B3`, `B4` for 4-day vertical-slice tasks.

## Status Rules
- Allowed progression: `BACKLOG -> READY -> IN_PROGRESS -> IN_REVIEW -> QA -> DONE`
- `DEPRECATED` is allowed as terminal replacement for removed work.
- Done-to-in-progress is rejected.
