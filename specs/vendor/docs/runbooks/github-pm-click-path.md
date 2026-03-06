# GitHub PM Click Path (Where To Click)

This guide is for day-to-day PM tracking using only GitHub.

Repository used in examples:
- `stevelefi/autotrading`

## Quick Links
1. Actions: [https://github.com/stevelefi/autotrading/actions](https://github.com/stevelefi/autotrading/actions)
2. Issues: [https://github.com/stevelefi/autotrading/issues](https://github.com/stevelefi/autotrading/issues)
3. PM Summary issue query: [https://github.com/stevelefi/autotrading/issues?q=is%3Aissue+%22%5BPM%5D+Program+Status%22](https://github.com/stevelefi/autotrading/issues?q=is%3Aissue+%22%5BPM%5D+Program+Status%22)
4. Project boards: [https://github.com/stevelefi/autotrading/projects](https://github.com/stevelefi/autotrading/projects)

## 1) Open Daily PM Dashboard
1. Click `Issues`.
2. Search for `[PM] Program Status`.
3. Open the issue and review:
- `Status` totals
- `By Team`
- `By Risk Tier`
- `By Milestone`
- `Escalations`

If no summary issue exists, run `pm-summary` workflow manually (section 3 below).

## 2) Open Execution Board
1. Click `Projects`.
2. Open the active delivery board.
3. Use filters:
- `label:plan-sync`
- `label:risk:p0` or `label:risk:p1`
- `label:status:in-progress`
4. Sort/group by `Status` first, then by `Owner Team`.

## 3) Run PM Workflows Manually
Go to `Actions` and run these workflows in order.

### A. `plan-sync` (sync tasks to issues/projects)
1. Click workflow: `plan-sync`.
2. Click `Run workflow`.
3. Select branch `main`.
4. Click `Run workflow` confirm.

### B. `pm-summary` (refresh dashboard issue)
1. Click workflow: `pm-summary`.
2. Click `Run workflow`.
3. Select branch `main`.
4. Click `Run workflow` confirm.

### C. `excel-export` (generate PM workbook)
1. Click workflow: `excel-export`.
2. Click `Run workflow`.
3. Select branch `main`.
4. Choose `commit_reports`:
- `false` for artifact-only download
- `true` to also commit into reports branch
5. Click `Run workflow` confirm.

## 4) Download Excel Report
1. Open the latest successful `excel-export` run.
2. Scroll to `Artifacts`.
3. Click `plan-tracker-report` and download.
4. Open workbook:
- `Roadmap`
- `By Team`
- `By Service`
- `By Risk`
- `Done Evidence`
- `Policy Changes`

## 5) Verify Data Is Fresh
1. In `[PM] Program Status`, check `Generated (UTC)` timestamp.
2. In `Actions`, confirm latest `plan-sync` run is green.
3. In `Issues`, spot-check one task issue title format: `[ATP-XXXX] ...`.
4. In project board, confirm status labels match `docs/tasks.yaml`.

## Screenshot Checklist (Team Template)
Capture these once from your real GitHub UI and store under:
- `docs/assets/images/pm/`

Suggested files:
1. `pm-01-issues-search.png` (Issues tab + PM summary search)
2. `pm-02-summary-issue.png` (PM summary body sections)
3. `pm-03-project-board.png` (Project board filters/grouping)
4. `pm-04-actions-run-workflow.png` (Run workflow button)
5. `pm-05-excel-artifact-download.png` (Artifacts download panel)

After adding images, embed them in this page using Markdown:
```md
![PM summary issue](../assets/images/pm/pm-02-summary-issue.png)
```

## Related Docs
1. [PM Dashboard Checklist](./pm-dashboard-checklist.md)
2. [PM Agent Operations Runbook](./pm-agent-operations.md)
3. [Plan Sync Ops Runbook](./plan-sync-ops.md)
4. [Task Tracking Guide](../TASK_TRACKING_GUIDE.md)

