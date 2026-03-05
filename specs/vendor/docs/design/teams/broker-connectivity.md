# Broker Connectivity Team Guide

## Scope
Owns IBKR connectivity, order submission adapter, callback processing, and connector failover behavior.

## Owned Services
- `ibkr-connector-service`

## Core Responsibilities
- Maintain single active writer semantics.
- Map `order_ref` and broker identifiers (`perm_id`, `exec_id`).
- Emit accurate order/fill events.
- Support startup and incident reconciliation inputs.

## Reliability Requirements
- No duplicate broker submission for same order intent.
- Callback processing idempotent.
- Connectivity incidents must emit alerts immediately.

## Operational Playbooks
- Reconnect policy with backoff.
- Connector restart procedure.
- Broker API version compatibility check.

## Testing Responsibilities
- Broker simulator integration tests.
- Delayed callback and duplicate callback tests.
- Connector crash/restart recovery tests.
