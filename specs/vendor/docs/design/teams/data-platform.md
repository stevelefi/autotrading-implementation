# Data Platform Team Guide

## Scope
Owns durable data consistency patterns across schema, persistence, and event reliability paths.

## Owned Components/Repos
- Components: Postgres schema and migrations, outbox/inbox reliability model, topic/key governance
- Repos: `autotrading-implementation`

## Core Responsibilities
- Maintain schema evolution and migration safety.
- Enforce idempotency keys, dedupe indexes, and replay-safe constraints.
- Own outbox/inbox contract integrity and recovery patterns.
- Support replay/rebuild and reconciliation data correctness.

## Non-Negotiables
- No destructive schema changes during active trading windows.
- Outbox append and domain mutations must share transactional boundaries.
- Replay must not produce duplicate effective side effects.

## Handoffs
- Inbound from Trading Core and Broker Connectivity: event and state mutation requirements.
- Inbound from SRE: operational limits and recovery requirements.
- Outbound to API/UI and QA/Release: reliable projection and certification data.

## Acceptance Signals
- Migration apply checks pass in CI.
- Replay and restore drills complete without unexplained drift.
- Outbox/inbox duplicate suppression metrics remain within thresholds.

