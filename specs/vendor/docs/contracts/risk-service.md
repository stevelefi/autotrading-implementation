# Risk Service Contract

## Owner Team
Trading Core + Policy Platform

## Responsibilities
- Consume signal decisions over gRPC.
- Evaluate policies via co-located OPA sidecar.
- Emit approve/reject outcomes to order service over gRPC.
- Emit risk events on violations.
- Emit policy evaluation audit events for every decision attempt.

## Inbound Interface
gRPC `RiskDecisionService.EvaluateSignal`

Required source attribution fields:
- `trade_event_id`
- `raw_event_id`
- `principal_id`
- `source_system`
- `origin_source_type`
- `origin_source_event_id` (required when origin is `EXTERNAL_SYSTEM`)

## Outbound Interfaces
- gRPC `OrderCommandService.CreateOrderIntent`
- Kafka `risk.events.v1`
- Kafka `policy.evaluations.audit.v1`

## OPA Topology Contract
- OPA sidecar runs per `risk-service` pod.
- OPA evaluation call is local HTTP inside pod network boundary.
- Remote/serverless policy evaluation is out of scope for hot path.

## Decision Payload (CreateOrderIntent Request)
```json
{
  "request_context": {
    "trace_id": "uuid",
    "request_id": "req-risk-20260303-000123",
    "idempotency_key": "sig-20260303-000123",
    "principal_id": "svc-risk"
  },
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "signal_id": "sig-20260303-000123",
  "decision": "ALLOW",
  "policy_version": "2026.03.03-1",
  "policy_rule_set": "prod-default",
  "matched_rule_ids": ["SESSION_WINDOW", "MAX_NET_POSITION"],
  "deny_reasons": [],
  "failure_mode": "NONE",
  "constraints": {
    "max_qty": 100,
    "max_net_position": 100,
    "cooldown_seconds": 15
  }
}
```

## Reject Event Example (`risk.events.v1`)
```json
{
  "decision": "DENY",
  "deny_reasons": ["OUTSIDE_TRADING_WINDOW", "DAILY_LOSS_LIMIT_REACHED"]
}
```

## Policy Evaluation Contract
- OPA request timeout: 200 ms.
- OPA unavailable: fail-closed for opening orders.
- OPA input schema: `opa.policy.input.v1`.
- OPA output schema: `opa.policy.decision.v1`.
- Schema mismatch is treated as `OPA_SCHEMA_ERROR` and must fail closed.
- `risk-service` readiness remains false until active valid bundle is loaded.

## Reason Code Taxonomy
- `POLICY_DENY`
- `OPA_TIMEOUT`
- `OPA_UNAVAILABLE`
- `OPA_SCHEMA_ERROR`
- `BUNDLE_LOAD_ERROR`

## Policy Explainability Contract
Every decision passed to downstream systems must include:
- `decision` (`ALLOW` or `DENY`)
- `policy_version`
- `policy_rule_set`
- `matched_rule_ids`
- `deny_reasons` (for denials)
- `failure_mode`

Every evaluation attempt must emit `policy.evaluations.audit.v1` with:
- `trace_id`
- `agent_id`
- `signal_id`
- `decision`
- `policy_version`
- `policy_rule_set`
- `matched_rule_ids`
- `deny_reasons`
- `failure_mode`
- `latency_ms`

## SLO
- Risk decision gRPC latency p95 &lt;= 20 ms (excluding caller network delay).

## Failure Behavior
- Policy engine unavailable => reject and raise P1 alert.
- OPA timeout => reject and raise P1 alert.
- OPA output schema invalid => reject and raise P1 alert.
- Bundle load failure => keep last-known-good bundle; if no valid active bundle, reject and raise P1 alert.
- Invalid signal payload => emit `risk.events.v1` with `event_type=INVALID_SIGNAL` and return gRPC invalid argument.
- Missing lineage/source attribution fields => reject and emit `risk.events.v1` with `event_type=INVALID_SIGNAL_SOURCE`.
- Order service unavailable => retry transient errors with same `idempotency_key`; fail closed after bounded retries.

## Related Contracts
- [Rule Engine (OPA)](../RULE_ENGINE_OPA.md)
- [Policy Bundle Contract](./policy-bundle-contract.md)
- [Policy Decision Audit Contract](./policy-decision-audit-contract.md)
- [OPA Input Schema](./schemas/opa.policy.input.v1.json)
- [OPA Decision Schema](./schemas/opa.policy.decision.v1.json)
