# Monitoring API Contract

## Owner Team
API/UI

## Responsibilities
- Operator control plane (`kill-switch`, trading mode, reconciliation controls).
- Query plane for agents, orders, positions, and performance.
- Real-time dashboard updates via SSE.
- Backward-compatible visibility for ingress lifecycle and routed trade-event outcomes.

## Service Role
`monitoring-api` is the public control/query edge for operators.
It does not own order execution state and does not own new event-ingress submission.
New manual/external event intake is owned by `ingress-gateway-service`.

## Authentication and Authorization
- Read endpoints: authenticated `viewer` or above.
- Mutating endpoints: `operator` or `admin`.
- Protected control actions require `admin`.
- Mutations require `X-Actor-Id` and `X-Request-Id`.

## Endpoint Groups

### Agent Endpoints
- `POST /api/v1/agents`
- `GET /api/v1/agents/{agentId}`
- `PUT /api/v1/agents/{agentId}/status`
- `PUT /api/v1/agents/{agentId}/config`
- `GET /api/v1/agents/{agentId}/positions`
- `GET /api/v1/agents/{agentId}/orders`
- `GET /api/v1/agents/{agentId}/performance`

### Trade Event Intake Endpoints (Deprecated Compatibility)
- `POST /api/v1/trade-events/manual`
- `POST /api/v1/trade-events/external`

### System Control Endpoints
- `GET /api/v1/system/health`
- `GET /api/v1/system/consistency-status`
- `POST /api/v1/system/kill-switch`
- `POST /api/v1/system/trading-mode`

### Reconciliation Endpoints
- `POST /api/v1/system/reconciliation/start`
- `GET /api/v1/system/reconciliation/{runId}`
- `POST /api/v1/system/reconciliation/{runId}/ack-resume`
- `GET /api/v1/orders/{orderIntentId}/consistency`

### Instrument Endpoints
- `POST /api/v1/instruments`
- `GET /api/v1/instruments`
- `PUT /api/v1/instruments/{instrumentId}/status`
- `GET /api/v1/instruments/{instrumentId}/active-contract`

### Streaming Endpoint
- `GET /api/v1/stream/events` (SSE)

## Trade Event Intake Contract (Deprecated Compatibility)
New integrations should use `ingress-gateway-service` (`/ingress/v1/...`) endpoints.

Legacy manual/external endpoints are retained during migration as compatibility paths.

Deprecation timeline:
- Announced: March 4, 2026.
- New integrations blocked on these endpoints: effective immediately.
- Removal target: after ingress OpenAPI parity and one full release cycle of migration validation.

Manual intake request must include:
- actor identity headers (`X-Actor-Id`, `X-Request-Id`)
- `idempotency_key`
- routing fields (`agent_id`, `instrument_id`, `side`, `qty`)

External intake request must include:
- integration identity (`integration_id`)
- `source_event_id`
- `idempotency_key`
- routing fields (`agent_id`, `instrument_id`, `side`, `qty`)

Both intake endpoints must:
1. validate legacy payload shape and normalize into ingress envelope fields,
2. forward to `ingress-gateway-service` (`/ingress/v1/events`) with same `idempotency_key`,
3. return ingress acceptance metadata (`ingress_event_id`, `status`, `received_at`) to caller,
4. return prior acceptance metadata on duplicate submissions,
5. avoid direct publish from `monitoring-api` for these compatibility endpoints.

## Mutating Control Example
Request (`POST /api/v1/system/kill-switch`):
```json
{
  "enabled": true,
  "reason": "manual-risk-intervention"
}
```

Response:
```json
{
  "trace_id": "trc-ops-1193",
  "data": {
    "kill_switch": "ON",
    "trading_mode": "FROZEN",
    "updated_at": "2026-03-04T17:10:04.118Z"
  }
}
```

## SSE Contract
Event types:
- `system.health`
- `order.status`
- `risk.event`
- `position.update`
- `pnl.snapshot`
- `reconciliation.update`

Reconnect behavior:
- Supports `Last-Event-ID` replay point.
- Events are delivered in publish order per connection.

Policy explainability requirement for rejected decisions:
- `risk.event` payload for reject outcomes must include:
  - `policy_version`
  - `policy_rule_set`
  - `matched_rule_ids`
  - `deny_reasons`
  - `failure_mode`
- These fields are sourced from `policy.evaluations.audit.v1` projections.

## Error and Trace Contract
- Every success and failure includes `trace_id`.
- Errors use standard envelope from `docs/contracts/common.md`.
- `error.code` must be stable and machine-parseable.

## SLO
- Query endpoint latency p95 `&lt;= 300 ms`.
- SSE publish lag p95 `&lt;= 2 seconds`.

## Failure Behavior
- If downstream projection store unavailable, return structured 5xx with `trace_id`.
- Mutating endpoints fail closed if audit sink unavailable.
- Control mutations must never partially apply.

## Implementation Notes
- Full endpoint request/response examples are maintained in [API Specification](../API_SPEC.md).
- Machine-readable endpoint definitions are maintained in `docs/contracts/monitoring-api.openapi.yaml`.
- Contract synchronization is verified with `./scripts/validate-api-contracts.sh`.
- Any endpoint changes must update both this contract and `docs/API_SPEC.md`.
- New ingress submissions and protocol contracts are defined in [Ingress Gateway Service Contract](./ingress-gateway-service.md).
