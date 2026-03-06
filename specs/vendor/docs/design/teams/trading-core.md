# Trading Core Team Guide

## Scope
Owns deterministic trade lifecycle behavior from routed signal to order lifecycle outcomes.

## Owned Components/Repos
- Components: `event-processor-service`, `agent-runtime-service`, `risk-service` integration path, `order-service`
- Repos: `autotrading-implementation`

## Core Responsibilities
- Maintain legal order state transitions and lifecycle invariants.
- Enforce command-path idempotency and replay-safe behavior.
- Enforce timeout, freeze, and reconciliation control points.
- Produce reliable command-path contracts for dependent teams.

## Non-Negotiables
- Unknown order state must trigger freeze controls.
- Command retries must not duplicate effective intent/submit behavior.
- Contract changes must be reflected in canonical docs before rollout.

## Handoffs
- Inbound from Policy Platform: policy decision contract and reason codes.
- Inbound from Broker Connectivity: normalized status/fill callback contracts.
- Outbound to API/UI and SRE: lifecycle events, alerts, and reconciliation hooks.

## Acceptance Signals
- Timeout/freeze/reconciliation scenarios pass end-to-end.
- No duplicate effective order intents in retry tests.
- Milestone evidence links are attached for DONE tasks.

