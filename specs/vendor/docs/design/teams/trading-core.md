# Trading Core Team Guide

## Scope
Owns strategy signal lifecycle, risk integration, order state transitions, timeout handling, and freeze controls.

## Owned Services
- `event-processor-service`
- `agent-runtime-service`
- `risk-service` integration logic
- `order-service`

## Interfaces
- Inbound: normalized ingress events and strategy signals
- Outbound: routed trade events, order intents, risk events to monitoring

## Key Deliverables
1. Deterministic order state machine implementation.
2. 60-second timeout watchdog.
3. Freeze mode gate in opening order paths.
4. Reconciliation initiation hooks.

## Non-Negotiable Checks
- Every signal must include idempotency key.
- Unknown state must trigger frozen mode.
- Done tickets require test evidence link.

## Testing Responsibilities
- Unit tests for transition guards.
- Integration tests for timeout/freeze behavior.
- Chaos scenarios for missing callbacks.

## Handoff Inputs
- Policy decisions from OPA contract.
- Connector callback contract and dedupe semantics.

## Handoff Outputs
- Order ledger updates.
- Structured risk and system alerts.
