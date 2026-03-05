# Data Platform Team Guide

## Scope
Owns database design, migrations, event persistence patterns, data quality, and performance read models.

## Owned Areas
- Postgres schema and Flyway migrations
- Kafka topic governance
- Outbox/inbox frameworks
- Position/PnL derivation support

## Critical Constraints
- Unique idempotency keys and execution keys.
- Strong indexes for ledger and deadline scans.
- Zero destructive migration during trading hours.

## Data Quality Responsibilities
- Schema validation in CI.
- Backfill/replay tooling design.
- Reconciliation diff data quality checks.

## Recovery Responsibilities
- Backup/restore testing.
- Replay verification procedures.
