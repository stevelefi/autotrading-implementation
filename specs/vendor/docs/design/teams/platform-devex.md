# Platform DevEx Team Guide

## Scope
Owns developer workflow automation, spec governance tooling, and planning system reliability.

## Owned Components/Repos
- Components: plan-sync automation, docs build/release workflows, spec drift verification
- Repos: `autotrading`, `autotrading-devops`

## Core Responsibilities
- Maintain task governance automation and validation policies.
- Maintain spec pinning and drift detection controls across implementation repos.
- Maintain docs site pipeline health and documentation quality gates.
- Maintain developer enablement tooling for repeatable workflows.

## Non-Negotiables
- `docs/tasks.yaml` remains canonical for planning automation.
- Implementation repos must verify pinned spec snapshots.
- No task deletion; deprecated workflow only.

## Handoffs
- Inbound from Program Lead: planning priorities and governance constraints.
- Inbound from SRE and QA/Release: release-gate automation requirements.
- Outbound to all teams: automation tooling, CI guardrails, and governance runbooks.

## Acceptance Signals
- plan-sync validate/sync/summary workflows run reliably.
- spec drift checks remain green across consumer repos.
- docs pipeline builds pass strict mode consistently.

