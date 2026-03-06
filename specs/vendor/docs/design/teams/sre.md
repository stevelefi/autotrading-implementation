# SRE Team Guide

## Scope
Owns runtime reliability, incident operations, and deployment safety controls.

## Owned Components/Repos
- Components: runtime observability stack, alert routing, deployment safety gates
- Repos: `autotrading-devops`

## Core Responsibilities
- Maintain health, alerting, and rollback controls for runtime services.
- Operate drill programs for outage, timeout, and reconcile scenarios.
- Validate runtime readiness and release-window operational posture.
- Own production incident response and post-incident hardening requirements.

## Non-Negotiables
- Command-path dependency degradation must fail closed.
- P0/P1 alert routes must remain actionable and tested.
- Rollback and freeze procedures must be executable under incident load.

## Handoffs
- Inbound from Platform DevEx: CI/CD and deployment automation changes.
- Inbound from Trading Core and Broker Connectivity: runtime risk signals.
- Outbound to QA/Release and Program Lead: operational readiness evidence.

## Acceptance Signals
- Drill logs show deterministic mitigation and recovery behavior.
- Observability baselines cover timeout/freeze/idempotency reliability indicators.
- Release readiness gates are met with runbook evidence.

