# PM Agent Operations Runbook

## Purpose
Define the daily operating loop for the PM automation agent that tracks delivery health from `docs/tasks.yaml` and GitHub issues/projects.

## Sources of Truth
- Canonical task ledger: `docs/tasks.yaml`
- Governance schema: `docs/tasks.schema.json`
- GitHub execution layer: issues + project fields
- Summary channel: issue titled `[PM] Program Status`

## Automation Commands
```bash
pip install -r requirements-plan-sync.txt
python tools/plan_sync.py validate --tasks docs/tasks.yaml
python tools/plan_sync.py sync-github --tasks docs/tasks.yaml --repo <owner/repo>
python tools/plan_sync.py pm-summary --tasks docs/tasks.yaml --repo <owner/repo> --issue-title "[PM] Program Status"
```

## Daily Cadence
1. Validate `docs/tasks.yaml`.
2. Sync task ledger to GitHub issues/project fields.
3. Publish PM summary issue update.
4. Escalate blocked/high-risk work based on rules below.

## Escalation Rules
1. Blocked candidate:
- task in `IN_PROGRESS` or `IN_REVIEW` with no GitHub issue update for more than 3 days.
2. High-risk stale:
- `P0` or `P1` task not in terminal status with no update for more than 24 hours.
3. Compliance gap:
- task in `DONE` with empty `evidence_url`.

## Weekly Milestone Rollup
1. Review summary totals by `status`, `owner_team`, `risk_tier`, `milestone`, `release_tag`.
2. Confirm high-risk tasks have explicit owners and target dates.
3. Confirm blockers have resolution actions in comments/linked issues.

## Failure Handling
1. If validation fails, stop sync and fix `docs/tasks.yaml` first.
2. If GitHub sync fails, run `sync-github --dry-run` locally and verify token/project configuration.
3. If PM summary update fails, run `pm-summary --dry-run` and compare generated markdown.

## Evidence Expectations
1. Every completed task must include `evidence_url`.
2. Major milestone closeout must link summary issue and supporting artifacts.
3. Any manual override of automation output must be documented in issue comments.

