# Broker Connectivity Team Guide

## Scope
Owns broker command and callback integration reliability for IBKR connectivity paths.

## Owned Components/Repos
- Components: `ibkr-connector-service`
- Repos: `autotrading-implementation`

## Core Responsibilities
- Enforce single active writer semantics for broker commands.
- Normalize and dedupe callback payloads (`perm_id`, `exec_id`, status stream).
- Provide reconnect and reconciliation support semantics.
- Emit status/fill events aligned to contract definitions.

## Non-Negotiables
- No duplicate broker submission for the same effective command.
- Callback replay must be idempotent.
- Broker integration incidents must emit actionable alerts.

## Handoffs
- Inbound from Trading Core: submit/cancel/replace command requests.
- Inbound from SRE: runtime health constraints and incident controls.
- Outbound to Trading Core and Data Platform: normalized status/fill streams.

## Acceptance Signals
- Duplicate callback tests pass with no duplicated side effects.
- Connector restart/reconnect drills maintain consistency.
- Reconciliation hooks are callable with deterministic outcomes.

