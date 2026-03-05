# Event Processor Service Contract

## Owner Team
Trading Core

## Responsibilities
- Consume canonical ingress events from `ingress.events.normalized.v1`.
- Validate routing intent and required lineage fields.
- Transform ingress payloads to domain-routed trade events.
- Publish `trade.events.routed.v1` for agent processing.
- Emit diagnostics for unroutable or invalid payloads.

## Input Topic
`ingress.events.normalized.v1`

Required identity/lineage:
- `ingress_event_id`
- `trace_id`
- `idempotency_key`
- source identity fields

## Output Topic
`trade.events.routed.v1`

Required lineage preservation:
- `ingress_event_id`
- `raw_event_id`
- `trade_event_id`
- `source_type`
- `source_event_id`

## Routing Rules
1. Validate `event_intent` and required routing fields.
2. Resolve target `agent_id` and partition key.
3. Produce routed trade event with deterministic keying.
4. Reject unroutable events to `ingress.errors.v1`.

## Delivery Semantics
- Producer: outbox-backed at-least-once.
- Consumer behavior: idempotent transformation using `idempotency_key` + `ingress_event_id`.

## Failure Behavior
- Invalid/missing route fields: do not publish routed event.
- Transformation error: publish diagnostic event and alert.
- Duplicate input event: no duplicate routed publish.

## Related Contracts
- [Ingress Gateway Service Contract](./ingress-gateway-service.md)
- [Kafka Event Contracts](../KAFKA_EVENT_CONTRACTS.md)
- [Agent Runtime Contract](./agent-runtime.md)
