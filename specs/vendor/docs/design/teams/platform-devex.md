# Platform DevEx Team Guide

## Scope
Owns CI/CD workflows, plan-sync automation tooling, task governance enforcement, and developer productivity guardrails.

## Owned Components
- `.github/workflows/*plan*`
- `tools/plan-sync`
- task schema validation and policy checks

## Non-Negotiable Rules
- `docs/tasks.yaml` is machine source of truth.
- PRs must pass plan validation checks.
- No task deletion in ledger; deprecate instead.

## Outputs
- Reliable issue/project sync.
- Daily Excel exports for stakeholders.
- Runbooks for sync failure and retries.
