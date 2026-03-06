# API/UI Team Guide

## Scope
Owns ingress-facing contracts and operator monitoring/control workflows.

## Owned Components/Repos
- Components: `ingress-gateway-service`, `monitoring-api`, dashboard UI flows
- Repos: `autotrading-implementation`

## Core Responsibilities
- Maintain ingress and monitoring API contract behavior.
- Maintain SSE/dashboard flows for frozen, unknown, and reconcile states.
- Ensure operator actions are auditable and role-constrained.
- Ensure legacy intake paths forward to ingress controls.

## Non-Negotiables
- Mutating controls require actor metadata and audit traceability.
- Frozen and unknown state visibility must be explicit in UI and API responses.
- API contract changes require synchronized docs/contract updates.

## Handoffs
- Inbound from Trading Core and Policy Platform: lifecycle/risk event contracts.
- Inbound from SRE: operational status and incident signal requirements.
- Outbound to Program Lead and QA/Release: operator readiness evidence.

## Acceptance Signals
- Contract validation and endpoint parity checks pass.
- Operator UAT scenarios for freeze/reconcile actions pass.
- UI/API outputs include required trace and explainability fields.

