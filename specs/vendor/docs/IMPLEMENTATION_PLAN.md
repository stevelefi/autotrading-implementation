# Plan-Change Automation Plan (GitHub + Python + Excel)

## Source of Truth
- `docs/IMPLEMENTATION_PLAN.md` is the human-readable plan.
- `docs/tasks.yaml` is the machine-readable task ledger.
- `docs/tasks.schema.json` defines required fields and formats.
- `.github/plan-sync-config.yaml` (from example) is the runtime automation config for GitHub variables/secrets.
- Bootstrap command: `python tools/plan_sync_bootstrap.py --config .github/plan-sync-config.yaml`

## Tooling
- Python CLI module: `tools/plan_sync`
- Commands:
  - `validate`: schema and governance checks.
  - `sync-github`: upsert GitHub issues/project fields.
  - `export-excel`: build stakeholder workbook from GitHub state.
  - `pm-summary`: publish daily PM status issue with escalations.

## Governance Rules
- Task IDs are immutable (`ATP-*`).
- Closed work must include evidence URL.
- Status transitions are constrained.
- Task deletion is not allowed in validation. If sync sees missing tasks, it marks legacy issues deprecated.

## Workflows
- `.github/workflows/plan-validate.yml`: PR checks for plan changes.
- `.github/workflows/plan-sync.yml`: sync tasks to GitHub on merge.
- `.github/workflows/excel-export.yml`: daily/manual Excel export.
- `.github/workflows/pm-summary.yml`: daily PM summary issue refresh.

## Reporting
- Excel output: `reports/plan-tracker-YYYY-MM-DD.xlsx`
- Sheets: `Roadmap`, `By Team`, `By Service`, `By Risk`, `Done Evidence`, `Policy Changes`.
