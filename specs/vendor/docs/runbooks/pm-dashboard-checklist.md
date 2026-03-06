# PM Dashboard Checklist (3-Minute Morning Review)

Use this every morning to get project health quickly without deep diving every issue.

## Minute 0-1: Topline Status
1. Open GitHub issue: `[PM] Program Status`.
2. Check:
- Total tracked tasks
- Status mix (`BACKLOG`, `READY`, `IN_PROGRESS`, `IN_REVIEW`, `QA`, `DONE`)
- Risk mix (`P0`/`P1` first)

If `P0` or `P1` count increased since yesterday, mark this as immediate follow-up.

## Minute 1-2: Escalations
Review the escalation section in `[PM] Program Status`:
1. `Blocked candidates` (>3 days in `IN_PROGRESS`/`IN_REVIEW`)
2. `P0/P1 stale` (>24h no update)
3. `DONE missing evidence_url`

Action rule:
1. For each listed task, confirm owner and next action in the task issue comment thread.
2. If owner/next action is missing, comment and assign by end of day.

## Minute 2-3: Milestone Confidence
Check these aggregates in `[PM] Program Status`:
1. `By Milestone`
2. `By Release Tag`
3. `By Team`

Quick decision:
1. `On track`: no blocked `P0/P1`, no evidence gaps, healthy movement to `QA/DONE`.
2. `At risk`: any stale `P0/P1` or growing blocked set.
3. `Off track`: unresolved stale `P0/P1` across two daily reviews.

## Daily Action Template
Post one short daily PM update (in your team channel or standup note):
1. `Health`: On track / At risk / Off track
2. `Top risks`: 1-3 task IDs
3. `Owner actions`: who is doing what today
4. `Ask`: unblock needed from leadership/platform

Example:
`Health: At risk. Risks: ATP-A03 stale (P1), ATP-A07 blocked. Actions: @alice updates ATP-A03 by 2pm, @bob resolves blocker dependency by EOD. Ask: approve temporary reviewer support.`

## Weekly Monday Add-On (5 minutes)
1. Validate milestone trend week-over-week from `[PM] Program Status`.
2. Check that all `DONE` tasks have valid evidence URLs.
3. Ensure next 7-day `P0/P1` work has clear owner and target date in `docs/tasks.yaml`.

## Fast Links
1. [Task Tracking Guide](../TASK_TRACKING_GUIDE.md)
2. [PM Agent Operations Runbook](./pm-agent-operations.md)
3. [GitHub PM Click Path](./github-pm-click-path.md)
4. [Plan Sync Ops Runbook](./plan-sync-ops.md)
