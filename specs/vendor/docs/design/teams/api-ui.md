# API and UI Team Guide

## Scope
Owns ingress boundary APIs, operator control/query APIs, SSE event stream, dashboard UI, and workflow UX for incident/reconciliation operations.

## Owned Components
- `ingress-gateway-service`
- `monitoring-api`
- `dashboard-ui`

## Required Screens
- System health and trading mode
- Agent performance and orders
- Risk events timeline
- Reconciliation status and resume action

## API Requirements
- Every response includes `trace_id`.
- Mutating endpoints require actor metadata.
- Consistent error envelopes across endpoints.
- Legacy intake endpoints in `monitoring-api` must forward to ingress and not bypass ingress controls.

## UX Requirements
- Frozen mode must be visually prominent.
- Unknown order states must be highlighted.
- Operator actions require confirmation and logging.
