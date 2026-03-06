# AGENTS.md

## Required Pre-Implementation Read Path
Before writing code, agents must read:
1. \
2. \
3. Relevant service docs under \

## Spec Pinning Guardrail
1. \ is authoritative for spec baseline.
2. Implementation work must target the pinned \ only.
3. If task requests behavior outside pinned spec, stop and raise spec-change request.

## Blitz Contract Freeze Rule
1. Runtime API/topic/proto/schema changes are frozen during blitz.
2. Blocking defects that require contract change must be raised in spec repo first.
3. Contract change is allowed only after new tagged baseline and spec bump PR.

## PR Workflow Guardrail
1. Branch names must follow: \.
2. Every PR must link an issue and include pinned spec ref.
3. PR must include acceptance checklist and evidence links.

## Required Local Checks Before Commit
1. \
2. Run repo-specific tests/lints.

## Change Control
1. Any contract change starts in spec repo, not here.
2. After spec tag bump, run sync and open dedicated "spec bump" PR.
