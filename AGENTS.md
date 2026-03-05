# AGENTS.md

## Required Pre-Implementation Read Path
Before writing code, agents must read:
1. `specs/vendor/docs/contracts/**`
2. `specs/vendor/docs/source-of-truth/**`
3. Relevant service docs under `specs/vendor/docs/`

## Spec Pinning Guardrail
1. `SPEC_VERSION.json` is authoritative for spec baseline.
2. Implementation work must target the pinned `ref` only.
3. If task requests behavior outside pinned spec, stop and raise spec-change request.

## Required Local Checks Before Commit
1. `python tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json`
2. Run repo-specific tests/lints.

## Change Control
1. Any contract change starts in spec repo, not here.
2. After spec tag bump, run sync and open dedicated "spec bump" PR.
