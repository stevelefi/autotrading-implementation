# AGENTS.md

## Required Pre-Implementation Read Path
Before writing code, agents must:
1. Sync pinned spec docs from `SPEC_VERSION.json` into `specs/vendor`.
2. Read `specs/vendor/docs/contracts/**`.
3. Read `specs/vendor/docs/source-of-truth/**`.
4. Read relevant service docs under `specs/vendor/docs/`.
5. Read repo charter index `specs/vendor/docs/repo-charters/README.md` and applicable repo charter `specs/vendor/docs/repo-charters/autotrading-implementation.md`.

## Spec Pinning Guardrail
1. `SPEC_VERSION.json` is authoritative for spec baseline.
2. Implementation work must target the pinned `ref` only.
3. If task requests behavior outside pinned spec, stop and raise spec-change request.

## Blitz Contract Freeze Rule
1. Runtime API/topic/proto/schema changes are frozen during blitz.
2. Blocking defects that require contract change must be raised in spec repo first.
3. Contract change is allowed only after new tagged baseline and spec bump PR.

## PR Workflow Guardrail
1. Branch names must follow: `codex/<repo>-<issue>-<short-topic>`.
2. Every PR must link an issue and include pinned spec ref.
3. PR must include acceptance checklist and evidence links.

## Required Local Checks Before Commit
1. Run `tools/spec_sync.py sync` using `repo_url` and `ref` from `SPEC_VERSION.json`.
2. `python tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`.
3. Run repo-specific tests/lints.
4. Confirm no `specs/**` files are staged (spec sync artifacts are local-only).

## Change Control
1. Any contract change starts in spec repo, not here.
2. After spec tag bump, run sync and open dedicated "spec bump" PR.
