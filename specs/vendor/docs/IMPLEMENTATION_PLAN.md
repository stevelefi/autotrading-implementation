# Plan-Change Automation Plan (GitHub + Java + Excel)

## Source of Truth
- `docs/IMPLEMENTATION_PLAN.md` is the human-readable plan.
- `docs/tasks.yaml` is the machine-readable task ledger.
- `docs/tasks.schema.json` defines required fields and formats.
- `.github/plan-sync-config.yaml` (from example) is the runtime automation config for GitHub variables/secrets.

## Tooling
- Java CLI module: `tools/plan-sync`
- Commands:
  - `validate`: schema and governance checks.
  - `sync-github`: upsert GitHub issues/project fields.
  - `export-excel`: build stakeholder workbook from GitHub state.

## Governance Rules
- Task IDs are immutable (`ATP-*`).
- Closed work must include evidence URL.
- Status transitions are constrained.
- Task deletion is not allowed in validation. If sync sees missing tasks, it marks legacy issues deprecated.

## Workflows
- `.github/workflows/plan-validate.yml`: PR checks for plan changes.
- `.github/workflows/plan-sync.yml`: sync tasks to GitHub on merge.
- `.github/workflows/excel-export.yml`: daily/manual Excel export.

## Reporting
- Excel output: `reports/plan-tracker-YYYY-MM-DD.xlsx`
- Sheets: `Roadmap`, `By Team`, `By Service`, `By Risk`, `Done Evidence`, `Policy Changes`.
