# 08 Change Control and Versioning

## Change Workflow
1. Create proposal PR against source-of-truth docs.
2. Link impacted contracts, tests, and teams.
3. Obtain lead approvals from affected streams.
4. Merge with updated tasks and ADR record.

## Versioning
- Source-of-truth version tracked by git tags and release notes.
- Contract version tracked by endpoint/topic version (`v1`, `v2`).

## Required Change Artifacts
- updated design section(s)
- updated contract examples
- migration or rollout notes
- acceptance test updates
- runbook delta

## Governance Checks
- `plan-validate` must pass.
- docs site build must pass.
- breaking contract changes require explicit migration section.
