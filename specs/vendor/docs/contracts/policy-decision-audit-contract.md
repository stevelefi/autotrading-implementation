# Policy Decision Audit Contract

## Owner Team
Policy Platform + Data Platform

## Purpose
Define the normalized audit record for every policy evaluation so decisions are explainable, traceable, and compliant.

## Audit Topic
- `policy.evaluations.audit.v1`

## Producer and Consumer
- Producer: `risk-service`
- Primary consumers: `monitoring-api` projection, compliance/audit pipelines, incident forensics workflows

## Required Fields
- `event_id`
- `event_type` (`policy.evaluations.audit`)
- `event_version`
- `occurred_at`
- `trace_id`
- `agent_id`
- `instrument_id`
- `signal_id`
- `decision` (`ALLOW` or `DENY`)
- `policy_version`
- `policy_rule_set`
- `matched_rule_ids`
- `deny_reasons`
- `failure_mode`
- `latency_ms`
- `source_system` (`risk-service`)

## Failure Mode Values
- `NONE`
- `OPA_TIMEOUT`
- `OPA_UNAVAILABLE`
- `OPA_SCHEMA_ERROR`
- `BUNDLE_LOAD_ERROR`

## Semantics
1. Emit one audit record per policy evaluation attempt.
2. For policy deny, set:
   - `decision=DENY`
   - `failure_mode=NONE`
   - `deny_reasons` populated by policy logic.
3. For technical fail-closed deny, set:
   - `decision=DENY`
   - `failure_mode` to technical cause
   - `deny_reasons` may be empty or include canonical reason code.
4. Preserve trace lineage with inbound `trace_id` and `signal_id`.

## Example Payload
```json
{
  "event_id": "evt-pol-20260304-001",
  "event_type": "policy.evaluations.audit",
  "event_version": 1,
  "occurred_at": "2026-03-04T18:02:11.229Z",
  "trace_id": "trc-7f2ad8f5",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "signal_id": "sig-8b4e",
  "decision": "DENY",
  "policy_version": "2026.03.04-3",
  "policy_rule_set": "prod-default",
  "matched_rule_ids": ["DAILY_LOSS_CAP"],
  "deny_reasons": ["DAILY_LOSS_LIMIT_REACHED"],
  "failure_mode": "NONE",
  "latency_ms": 8,
  "source_system": "risk-service"
}
```

## Operator/API Projection Requirements
`monitoring-api` must expose policy explanation fields for rejected decisions:
- `policy_version`
- `policy_rule_set`
- `matched_rule_ids`
- `deny_reasons`
- `failure_mode`

## Related Contracts
- [Risk Service Contract](./risk-service.md)
- [Rule Engine (OPA)](../RULE_ENGINE_OPA.md)
- [Kafka Event Contracts](../KAFKA_EVENT_CONTRACTS.md)
