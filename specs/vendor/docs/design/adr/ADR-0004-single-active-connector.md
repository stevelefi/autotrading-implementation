# ADR-0004: Single Active Broker Connector Writer

## Status
Accepted

## Decision
Allow only one active connector instance to submit broker orders.

## Consequences
- Lower duplicate submission risk.
- Requires lease/fencing and failover runbook.
