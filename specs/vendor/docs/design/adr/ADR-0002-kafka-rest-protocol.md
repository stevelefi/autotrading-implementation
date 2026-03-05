# ADR-0002: Kafka + REST Protocol Split

## Status
Accepted

## Decision
Use Kafka JSON for async streaming and REST JSON for sync command/query paths.

## Consequences
- Clear separation of data/event flow and control-plane operations.
- Simplifies observability and debugging boundaries.
