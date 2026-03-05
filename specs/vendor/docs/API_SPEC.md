# API Specification

This specification defines operator-facing contracts for `monitoring-api`.
It is the canonical API reference for implementation and testing.

## Machine-Readable Source
- OpenAPI contract: `docs/contracts/monitoring-api.openapi.yaml`
- Validation command: `./scripts/validate-api-contracts.sh`
- Validation guidance: `docs/API_CONTRACT_VALIDATION.md`

## API Surface Boundaries
- `monitoring-api` is the control/query API (`/api/v1/...`).
- Event ingestion is owned by `ingress-gateway-service` (`/ingress/v1/...` and gRPC/WebSocket).
- Legacy `monitoring-api` trade-intake endpoints remain compatibility-only and should not be used for new integrations.

## Global Conventions
- Base path: `/api/v1`
- Content type: `application/json`
- Time format: UTC ISO-8601
- Every response includes `trace_id`
- Mutating endpoints require `X-Actor-Id` and `X-Request-Id`

## Auth and Roles
- `viewer`: read-only endpoints.
- `operator`: operational mutations (except protected controls).
- `admin`: protected controls (`kill-switch`, `trading-mode`, reconciliation resume).

## Standard Success Envelope
```json
{
  "trace_id": "trc-2a9a2fd5",
  "data": {}
}
```

## Standard Error Envelope
```json
{
  "trace_id": "trc-2a9a2fd5",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request payload is invalid.",
    "details": {
      "field": "qty",
      "reason": "must be between 1 and 100"
    }
  }
}
```

## Idempotency Rules
- Mutating endpoints that create resources must support idempotent behavior by request identity.
- Client retries on network/5xx must reuse same semantic request identity.
- Server must return the existing resource instead of creating duplicates.

## Endpoint Catalog

## Agents

### `POST /api/v1/agents`
Create agent configuration.

Request:
```json
{
  "agent_id": "agent_momo_01",
  "name": "Momentum Agent 01",
  "status": "ACTIVE",
  "strategy_type": "MOMENTUM",
  "risk_profile": {
    "max_order_qty": 100,
    "max_net_position": 100,
    "max_trades_per_day": 100,
    "max_daily_loss": 500.0,
    "cooldown_seconds": 15
  }
}
```

Response `201`:
```json
{
  "trace_id": "trc-1",
  "data": {
    "agent_id": "agent_momo_01",
    "status": "ACTIVE",
    "created_at": "2026-03-04T00:00:00Z"
  }
}
```

### `GET /api/v1/agents/{agentId}`
Return agent metadata and current status.

### `PUT /api/v1/agents/{agentId}/status`
Set status to `ACTIVE`, `PAUSED`, or `STOPPED`.

Request:
```json
{
  "status": "PAUSED",
  "reason": "manual-operator-action"
}
```

### `PUT /api/v1/agents/{agentId}/config`
Update risk profile and execution bounds.

### `GET /api/v1/agents/{agentId}/positions`
Returns current net position and PnL values.

### `GET /api/v1/agents/{agentId}/orders?from=&to=&status=&page=&size=`
Returns paginated order timeline and state transitions.

### `GET /api/v1/agents/{agentId}/performance?from=&to=`
Returns aggregate metrics and snapshot intervals.

## Trade Event Intake (Deprecated Compatibility)

New event submissions should use `ingress-gateway-service` endpoints described in
`docs/contracts/ingress-gateway-service.md`.
The endpoints below are retained for compatibility during migration.

Deprecation timeline:
- Announced: March 4, 2026.
- New integrations should not onboard to these endpoints.
- Removal target: after ingress OpenAPI parity and one release cycle of migration validation.

### `POST /api/v1/trade-events/manual`
Create a trade event from an authenticated trader action.

Request:
```json
{
  "idempotency_key": "manual-evt-20260304-0001",
  "agent_id": "agent_manual_01",
  "instrument_id": "eq_tqqq",
  "side": "BUY",
  "qty": 5,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "reason": "manual-operator-trigger",
  "source_event_id": "ui-evt-188120"
}
```

Response `202`:
```json
{
  "trace_id": "trc-man-1",
  "data": {
    "ingress_event_id": "ing-20260304-000412",
    "accepted": true,
    "received_at": "2026-03-04T00:00:01Z",
    "status": "ACCEPTED",
    "forwarded_to": "ingress-gateway-service"
  }
}
```

### `POST /api/v1/trade-events/external`
Create a trade event from an external integration.

Request:
```json
{
  "integration_id": "ext_tv_bridge_01",
  "source_event_id": "tv-alert-778912",
  "idempotency_key": "ext-tv-778912",
  "agent_id": "agent_ext_01",
  "instrument_id": "eq_tqqq",
  "side": "SELL",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "reason": "external-signal"
}
```

Response `202`:
```json
{
  "trace_id": "trc-ext-1",
  "data": {
    "ingress_event_id": "ing-20260304-000998",
    "accepted": true,
    "received_at": "2026-03-04T00:00:02Z",
    "status": "ACCEPTED",
    "forwarded_to": "ingress-gateway-service"
  }
}
```

Intake semantics for both endpoints:
1. Validate legacy payload and map it to canonical ingress envelope fields.
2. Forward submission to ingress (`/ingress/v1/events`) with preserved idempotency key and trace metadata.
3. Return ingress acceptance metadata (`ingress_event_id`, `status`, `received_at`) to caller.
4. Return prior acceptance metadata when duplicate idempotency key/source event is received.

## System Controls

### `GET /api/v1/system/health`
Service and dependency health summary.

### `GET /api/v1/system/consistency-status`
Consistency indicators:
- unknown order count
- reconciliation status
- trading mode

### `POST /api/v1/system/kill-switch`
Protected control endpoint.

Request:
```json
{
  "enabled": true,
  "reason": "manual-risk-intervention"
}
```

### `POST /api/v1/system/trading-mode`
Protected control endpoint.

Request:
```json
{
  "mode": "FROZEN",
  "reason": "timeout-breach"
}
```

## Reconciliation

### `POST /api/v1/system/reconciliation/start`
Start reconciliation run.

Request:
```json
{
  "scope": "ALL",
  "reason": "ORDER_STATUS_TIMEOUT"
}
```

Response `202`:
```json
{
  "trace_id": "trc-rec-start",
  "data": {
    "run_id": "rec-20260304-001",
    "status": "STARTED"
  }
}
```

### `GET /api/v1/system/reconciliation/{runId}`
Return reconciliation run state and mismatch summary.

### `POST /api/v1/system/reconciliation/{runId}/ack-resume`
Operator acknowledgment to transition from `FROZEN` to `NORMAL` after clean reconciliation.

### `GET /api/v1/orders/{orderIntentId}/consistency`
Return order-level broker/ledger consistency details.

## Instruments

### `POST /api/v1/instruments`
Create instrument metadata.

### `GET /api/v1/instruments?asset_type=&status=&symbol=&page=&size=`
List instruments with filtering.

### `PUT /api/v1/instruments/{instrumentId}/status`
Activate/deactivate instrument.

### `GET /api/v1/instruments/{instrumentId}/active-contract`
Return active contract mapping for futures instruments (for example MNQ).

## Streaming (SSE)

### `GET /api/v1/stream/events`
Server-sent events for dashboard updates.

Event types:
- `system.health`
- `order.status`
- `risk.event`
- `position.update`
- `pnl.snapshot`
- `reconciliation.update`

`risk.event` payload for rejected decisions includes policy explainability fields:
- `policy_version`
- `policy_rule_set`
- `matched_rule_ids`
- `deny_reasons`
- `failure_mode`

Reconnect behavior:
- Supports `Last-Event-ID`.
- Server resumes from next available event after provided id.

## Status Codes
- `200` successful read/update
- `201` created
- `202` accepted (async operation started)
- `400` validation failure
- `401` unauthenticated
- `403` unauthorized
- `404` resource not found
- `409` conflict/idempotency duplicate semantics
- `422` business rule violation
- `500` internal error

## API Working Definition
An API endpoint is considered working only when:
1. OpenAPI path exists in `docs/contracts/monitoring-api.openapi.yaml`.
2. Endpoint heading exists in this document with semantics and example payload.
3. Contract-level behavior exists in `docs/contracts/monitoring-api.md`.
4. `./scripts/validate-api-contracts.sh` passes in CI.
5. Integration test evidence for success/failure cases is linked in the task tracker.

## API Readiness Checklist
An endpoint is considered ready when all are true:
1. Request/response examples are documented.
2. Error cases are documented with stable error codes.
3. Auth role is documented.
4. Idempotency/retry behavior is documented for mutations.
5. Test evidence exists for happy path + failure path.

## Contract Ownership
- Primary: API/UI Team
- Shared reviewers: Trading Core, SRE, Compliance
