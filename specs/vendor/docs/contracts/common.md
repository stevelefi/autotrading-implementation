# Common Contract Conventions

## Versioning
- REST APIs: `/api/v1/...`
- Kafka topics: `*.v1`
- JSON schema: `event_version` integer in envelope
- Breaking changes require new version namespace (`v2`) plus migration window.
- Deprecated endpoints/topics must include replacement path and removal milestone.

## Traceability
Every request and event must carry:
- `trace_id` (UUID)
- `ingress_event_id` for ingress-originated flows
- `agent_id` (when applicable)
- `raw_event_id` and `trade_event_id` for intake-driven flows
- `order_intent_id` (for order lifecycle flows)
- `idempotency_key` (for ingress/signal/intent creation)
- `source_type` and source identity lineage fields for routed trade-event-originated signals

## Time Standard
- Timestamps are UTC (`RFC3339` / ISO-8601)
- Example: `2026-03-03T21:40:00Z`

## Error Envelope
```json
{
  "trace_id": "2f7ed09c-4de6-4e28-9831-7f24337bc1eb",
  "error": {
    "code": "ORDER_TIMEOUT",
    "message": "No broker status received within 60 seconds.",
    "details": {
      "order_intent_id": "ord_123",
      "submission_deadline": "2026-03-03T21:41:00Z"
    }
  }
}
```

## Policy Failure Codes
Use stable reason codes for policy-path technical denies:
- `POLICY_DENY`
- `OPA_TIMEOUT`
- `OPA_UNAVAILABLE`
- `OPA_SCHEMA_ERROR`
- `BUNDLE_LOAD_ERROR`

## Idempotency Rules
- `idempotency_key` is mandatory on ingress, signal, and order-intent creation.
- Duplicate key must return existing resource state, not create a new one.
- Keys must be globally unique for minimum retention window of 7 days.

## Retry Policy (Default)
- Client retries only on network/5xx and must reuse same idempotency key.
- Service retries to Kafka are outbox-driven.
- Consumer handlers must be idempotent.

## Compatibility Rules
- Additive fields are allowed in `v1`.
- Existing required fields in `v1` must not be removed or renamed.
- Enum changes in `v1` must be additive only.
- Consumers should tolerate unknown fields in the current version.

## Extensibility Rules
- Any new public API must have a machine-readable contract (OpenAPI or protobuf).
- Any new topic must have a JSON schema artifact in `docs/contracts/schemas/`.
- Any new service must publish a contract page before implementation begins.

## Security Baseline
- Control-plane mutating APIs require authenticated actor identity.
- Actor metadata persisted to audit logs.
