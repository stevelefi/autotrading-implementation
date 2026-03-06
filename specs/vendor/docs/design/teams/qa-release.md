# QA/Release Team Guide

## Scope
Owns release quality gates, evidence standards, and promotion readiness decisions.

## Owned Components/Repos
- Components: test gate definitions, certification checklist, release evidence process
- Repos: `autotrading-devops`, `autotrading-implementation`

## Core Responsibilities
- Define and execute E2E, reliability, and soak test criteria.
- Validate gate evidence completeness before promotion.
- Enforce no-close rules for unresolved P0/P1 conditions.
- Maintain release decision records and certification artifacts.

## Non-Negotiables
- DONE work without evidence cannot pass final release gates.
- Promotion requires all mandatory scenario coverage.
- Critical defects block release until mitigated and revalidated.

## Handoffs
- Inbound from all implementation teams: test artifacts and change evidence.
- Inbound from SRE: drill results and operational readiness status.
- Outbound to Program Lead: go/no-go recommendation with evidence bundle.

## Acceptance Signals
- 10-day paper certification criteria pass with traceable evidence.
- Required gate matrix scenarios show consistent pass status.
- Release decisions have linked issue/task/artifact references.

