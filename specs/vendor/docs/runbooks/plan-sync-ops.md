# Plan Sync Operations Runbook

## Scope
This runbook covers failure handling for task validation, GitHub sync, Excel export, and PM summary jobs.

## Validation Failures (`plan-validate.yml`)
1. Check workflow logs for validation errors.
2. Fix `docs/tasks.yaml` fields, status transitions, or missing evidence URLs.
3. Re-run PR checks after pushing fix.

## GitHub Sync Failures (`plan-sync.yml`)
1. Confirm token scope includes:
   - `issues:write`
   - `projects:write` (if project sync enabled)
2. Re-run workflow using identical commit SHA.
3. Verify idempotency:
   - no duplicate issues by `task_id`
   - labels and state align with `tasks.yaml`
4. If project fields fail, disable project config and run issue-only sync.

## Excel Export Failures (`excel-export.yml`)
1. Check GitHub API rate limits and token scopes.
2. Re-run workflow manually.
3. Validate artifact presence and workbook sheet integrity.

## PM Summary Failures (`pm-summary.yml`)
1. Confirm token scope includes `issues:write`.
2. Re-run workflow manually.
3. Verify `[PM] Program Status` issue exists and body timestamp updates.
4. If update conflicts or malformed body occurs, run local dry-run and compare markdown output.

## Incident Escalation
- P1 (>30 min automation outage): Platform DevEx on-call.
- P0 (incorrect task state changes): freeze sync, restore previous `tasks.yaml`, and run dry-run sync before enabling.

## Recovery Commands
```bash
pip install -r requirements-plan-sync.txt
python tools/plan_sync.py validate --tasks docs/tasks.yaml
python tools/plan_sync.py sync-github --tasks docs/tasks.yaml --repo <owner/repo> --dry-run
python tools/plan_sync.py pm-summary --tasks docs/tasks.yaml --repo <owner/repo> --dry-run
```

## Secret + Variable Bootstrap
```bash
cp .github/plan-sync-config.yaml.example .github/plan-sync-config.yaml
# fill owner/repo/project_number/timezone/report_branch/plan_sync_github_token
python tools/plan_sync_bootstrap.py --config .github/plan-sync-config.yaml
```
