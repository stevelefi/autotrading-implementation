# Ingress Gateway Service Contract

## Owner Team
API/UI

## Responsibilities
- Unified event-ingress edge for external systems and Trader UI.
- Protocol adapters for WebHook, REST API, gRPC, and WebSocket.
- Authentication and authorization at ingress boundary.
- Envelope/schema validation and mandatory idempotency enforcement.
- Immutable raw-event persistence before normalized publish.
- Outbox-backed publish to `ingress.events.normalized.v1`.
- Reject/audit/diagnose invalid events through `ingress.errors.v1`.

## Service Role
`ingress-gateway-service` is the only public event-ingress edge for new event submissions.
It accepts events asynchronously, emits canonical normalized events, and does not execute trading logic.

## Producer Matrix
| Producer | Supported Protocols | Required Identity |
|---|---|---|
| External systems | WebHook, REST API, gRPC | HMAC (WebHook), token (REST), mTLS+token (gRPC) |
| Trader UI | REST API, WebSocket | JWT |

## Security Model
- WebHook: HMAC signature + replay-window timestamp checks.
- REST/WebSocket: JWT bearer authentication.
- gRPC: mTLS client identity + token metadata.
- Authorization must validate submit permission for target `agent_id` or `integration_id` scope.
- All accepted/rejected events must produce auditable security metadata.

## REST Endpoints
### `POST /ingress/v1/events`
Unified submit endpoint for Trader UI and external systems.

Required headers:
- `Authorization`
- `X-Request-Id`

Required body fields:
- `idempotency_key`
- `event_intent`
- `payload`

Conditional fields:
- `agent_id` (required for agent-scoped intents)
- `integration_id` (required for external source)
- `source_event_id` (required for external source)

Response `202`:
```json
{
  "trace_id": "trc-in-9012",
  "data": {
    "accepted": true,
    "ingress_event_id": "ing-20260303-000912",
    "received_at": "2026-03-03T22:00:00Z",
    "status": "ACCEPTED"
  }
}
```

## WebHook Endpoint
### `POST /ingress/v1/webhooks/{integrationId}`
Webhook-specific submit endpoint.

Required headers:
- `X-Signature`
- `X-Signature-Timestamp`
- `X-Request-Id`

Required body fields:
- `idempotency_key`
- `event_intent`
- `payload`

Semantics:
1. Verify signature and replay window before parsing payload.
2. Reject unauthorized/expired signatures.
3. Apply same normalization/idempotency pipeline as REST and gRPC.

## gRPC Endpoint
```proto
rpc PublishEvent(PublishEventRequest) returns (AcceptedResponse)
```

gRPC metadata requirements:
- mTLS authenticated client certificate.
- Authorization token metadata.
- Request-level `x-request-id`.

## WebSocket Contract
### `wss://.../ingress/v1/ws`
Inbound message types:
- `event.submit`

Outbound message types:
- `event.accepted`
- `event.validation_failed`
- `event.routed`
- `event.failed`

WebSocket response semantics:
1. Send `event.accepted` immediately for durable acceptance.
2. Send `event.validation_failed` or `event.failed` on ingestion failure.
3. Send `event.routed` when normalized event is published successfully.

## Normalized Output Contract
Output topic: `ingress.events.normalized.v1`

Required normalized fields:
- `ingress_event_id`
- `trace_id`
- `idempotency_key`
- `source_protocol`
- `source_type`
- `event_intent`
- `occurred_at`
- `principal`
- `payload`

Conditional normalized fields:
- `agent_id`
- `integration_id`
- `source_event_id`

Partition key policy:
- use `agent_id` when present,
- otherwise use `integration_id`.

## Idempotency Contract
- `idempotency_key` is mandatory for all protocols/sources.
- Duplicate submissions return previously accepted `ingress_event_id`.
- Duplicate submission must not publish additional normalized events.
- Minimum dedupe retention window is 7 days.

## Raw Persistence and Lineage
Ingress must persist immutable raw-event records before publish with:
- `ingress_event_id`
- `trace_id`
- source identity metadata
- auth metadata
- raw payload
- validation/audit status

No normalized event publish is allowed without durable raw record persistence.

## Error and Diagnostics Contract
Invalid or rejected events must:
1. return structured 4xx/401/403 error with `trace_id`,
2. write security/validation audit entry,
3. publish diagnostic record to `ingress.errors.v1`.

## SLO
- Ingress accept latency p95 `<= 150 ms` for accepted requests.
- Normalized publish lag p95 `<= 2 seconds` after durable acceptance.

## Failure Behavior
- AuthN/AuthZ failure: reject immediately and audit.
- Validation failure: reject and emit `ingress.errors.v1` diagnostic event.
- Outbox publish failure: keep accepted event durable and retry publish.
- Duplicate: return prior acceptance metadata without re-publish.

## Related Contracts
- [Kafka Event Contracts](../KAFKA_EVENT_CONTRACTS.md)
- [Common Contract Conventions](./common.md)
- [Monitoring API Contract](./monitoring-api.md)

## Contract Artifacts
- Protobuf: `docs/contracts/ingress-gateway-service.proto`
- JSON Schema (normalized topic): `docs/contracts/schemas/ingress.events.normalized.v1.json`
- JSON Schema (WebSocket messages): `docs/contracts/schemas/ingress.ws.events.v1.json`
