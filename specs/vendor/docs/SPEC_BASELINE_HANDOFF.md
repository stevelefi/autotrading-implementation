# Spec Baseline Tagging and Multi-Agent Handoff

This document defines how to freeze this spec repository and distribute a pinned baseline to implementation repositories.

## Scope for This Milestone
1. Service mesh is out of scope.
2. Baseline freeze scope is `M0 + M1`.
3. Consumer repositories use full docs snapshot (`docs/**`) from a pinned tag.
4. Implementation agents are assigned by stream team.
5. Versioning model is pinned git tags.

## Baseline Tag Standard
Current first implementation baseline tag:
- `spec-v1.0.0-m0m1`

Future tag format:
- `spec-v<major>.<minor>.<patch>-m<milestone>`
- Example: `spec-v1.1.0-m2`

Semver compatibility:
1. `major`: breaking contract change.
2. `minor`: additive behavior/contract change.
3. `patch`: non-breaking doc/clarification/fix.

## Consumer Repository Contract
Each implementation repo must contain:
1. `SPEC_VERSION.json`
2. `tools/spec_sync.py` (copied from this spec repo)
3. `specs/vendor/docs/**`
4. `specs/vendor/.spec-manifest.json` (generated)

Required sync/verify commands:
```bash
python tools/spec_sync.py sync \
  --repo-url https://github.com/stevelefi/autotrading.git \
  --ref spec-v1.0.0-m0m1 \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json

python tools/spec_sync.py verify \
  --dest specs/vendor \
  --version-file SPEC_VERSION.json
```

## `SPEC_VERSION.json` Template
```json
{
  "repo_url": "https://github.com/stevelefi/autotrading.git",
  "ref": "spec-v1.0.0-m0m1",
  "synced_at_utc": "2026-03-05T00:00:00Z",
  "dest": "specs/vendor"
}
```

## Stream-to-Agent Assignment
| Stream | Scope | Initial Implementation Repos |
|---|---|---|
| Trading Core | event-processor, agent-runtime, risk, order lifecycle | trading-core repos |
| Broker Connectivity | ibkr connector command and callback path | broker connector repo |
| Policy Platform | OPA bundles and policy governance | policy repo |
| Data Platform | schema, outbox/inbox, replay, projections | data repos |
| API/UI | ingress, monitoring-api, dashboard | api/ui repos |
| SRE/QA/Release | runtime operations, drills, release gates | infra/ops/qa repos |

## `AGENTS.md` Guardrail Template (Consumer Repos)
Add this section to each consumer repo `AGENTS.md`:

```md
## Spec-Pinned Implementation Rules
1. Read `SPEC_VERSION.json` before implementation.
2. Read `specs/vendor/docs/contracts/**` and `specs/vendor/docs/source-of-truth/**` before code changes.
3. Implement only against the pinned `ref` from `SPEC_VERSION.json`.
4. If requested behavior is missing from pinned spec, stop and request spec update PR in spec repo.
5. Run `python tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json` before finalizing.
```

## CI Drift Check Template (Consumer Repos)
Use this workflow in implementation repos:

```yaml
name: spec-drift-check

on:
  pull_request:
  workflow_dispatch:

jobs:
  verify-spec:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"

      - name: Verify pinned spec snapshot
        run: |
          python tools/spec_sync.py verify \
            --dest specs/vendor \
            --version-file SPEC_VERSION.json
```

## End-to-End Handoff Workflow
1. Confirm `docs/DELIVERABLES_AND_MILESTONES.md` and `docs/IMPLEMENTATION_PHASES.md` are approved for `M0 + M1`.
2. Create and push annotated baseline tag (`spec-v1.0.0-m0m1`).
3. Each consumer repo syncs from exact tag and commits:
   - `SPEC_VERSION.json`
   - `specs/vendor/docs/**`
   - `specs/vendor/.spec-manifest.json`
4. Consumer repos enforce drift check in CI.
5. Start implementation by stream with explicit tag reference in epics/issues.
6. For spec changes:
   - update spec repo first,
   - cut new tag,
   - bump consumer repos via dedicated spec-bump PR.

## Acceptance Checklist
1. Same tag synced in all implementation repos.
2. Fresh clone + sync + verify succeeds per repo.
3. CI blocks drift from pinned tag.
4. Agent execution follows pinned spec guardrails.
5. Cross-repo implementations of same contract use same tag ref.
