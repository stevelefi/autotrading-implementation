# Kafka Event Contracts

This file defines topic-level contracts and operational semantics for event-driven service communication.

Hybrid transport note:
- Kafka is the event backbone for ingress, broker callbacks, monitoring projections, and alerts.
- Real-time buy/sell command flow (`agent-runtime` -> `risk` -> `order` -> `ibkr-connector`) uses gRPC and is documented in `docs/contracts/protos/internal-command-plane.proto`.

## Topic Catalog
Active topics in target design:
- `ingress.events.normalized.v1`
- `ingress.errors.v1`
- `trade.events.routed.v1`
- `orders.status.v1`
- `fills.executed.v1`
- `policy.evaluations.audit.v1`
- `positions.updated.v1`
- `pnl.snapshots.v1`
- `risk.events.v1`
- `system.alerts.v1`

Compatibility-only (legacy transition topics, not used by new command path):
- `signals.generated.v1`
- `risk.decisions.v1`
- `orders.intents.v1`

Command-path transport rule:
- `agent-runtime-service -> risk-service -> order-service -> ibkr-connector-service` is gRPC-only in the target design.
- Legacy topics above are retained for migration/reference only and are not required in the current command path.

## Schema Artifact Coverage
The topic list is complete, but machine-readable schema coverage is currently partial.

| Topic | JSON Schema Artifact |
|---|---|
| `ingress.events.normalized.v1` | `docs/contracts/schemas/ingress.events.normalized.v1.json` |
| `ingress.errors.v1` | planned |
| `trade.events.routed.v1` | planned |
| `signals.generated.v1` | planned |
| `risk.decisions.v1` | planned |
| `orders.intents.v1` | planned |
| `orders.status.v1` | planned |
| `fills.executed.v1` | planned |
| `policy.evaluations.audit.v1` | `docs/contracts/schemas/policy.evaluations.audit.v1.json` |
| `positions.updated.v1` | planned |
| `pnl.snapshots.v1` | planned |
| `risk.events.v1` | planned |
| `system.alerts.v1` | planned |

## Common Envelope
```json
{
  "event_id": "uuid",
  "event_type": "ingress.events.normalized",
  "event_version": 1,
  "occurred_at": "2026-03-03T22:00:00Z",
  "trace_id": "uuid",
  "idempotency_key": "ext-tv-778912",
  "agent_id": "agent_momo_01",
  "instrument_id": "eq_tqqq",
  "ingress_event_id": "ing-20260303-000912",
  "raw_event_id": "raw-20260303-000912",
  "trade_event_id": "tev-20260303-000912",
  "source_protocol": "WEBHOOK",
  "source_type": "EXTERNAL_SYSTEM",
  "source_event_id": "tv-alert-778912",
  "source_system": "tv-bridge",
  "principal": {
    "principal_id": "ext_tv_bridge_01",
    "principal_type": "INTEGRATION",
    "auth_method": "HMAC"
  },
  "payload": {}
}
```

## Required Envelope Fields
| Field | Type | Required | Notes |
|---|---|---|---|
| `event_id` | UUID | yes | Unique per event |
| `event_type` | string | yes | Topic semantic type |
| `event_version` | int | yes | Increment on schema evolution |
| `occurred_at` | timestamp | yes | UTC |
| `trace_id` | UUID | yes | Correlation id |
| `idempotency_key` | string | conditional | Required for ingress, signal, and order-intent lifecycle |
| `agent_id` | string | conditional | Required for agent-scoped flows |
| `instrument_id` | string | conditional | Required for trading events |
| `ingress_event_id` | string | conditional | Required for ingress-originated events |
| `raw_event_id` | string | conditional | Required for routed trade events and lineage |
| `trade_event_id` | string | conditional | Required for routed trade events and downstream signals |
| `source_protocol` | string | conditional | `WEBHOOK`, `API`, `GRPC`, `WEBSOCKET` |
| `source_type` | string | conditional | `TRADER_UI`, `EXTERNAL_SYSTEM`, `AGENT_RUNTIME`, `SYSTEM_INTERNAL` |
| `source_event_id` | string | conditional | Upstream event identity for external/UI origins |
| `source_system` | string | conditional | e.g. `ingress-gateway-service`, `agent-runtime-service`, `tv-bridge` |
| `principal` | object | conditional | Normalized submitter identity for ingress-originated events |
| `payload` | object | yes | Topic-specific body |

## Topic Schemas (Payload)

## `ingress.events.normalized.v1`
```json
{
  "ingress_event_id": "ing-20260303-000912",
  "source_protocol": "WEBHOOK",
  "source_type": "EXTERNAL_SYSTEM",
  "source_event_id": "tv-alert-778912",
  "source_system": "tv-bridge",
  "event_intent": "TRADE_SIGNAL",
  "principal": {
    "principal_id": "ext_tv_bridge_01",
    "principal_type": "INTEGRATION",
    "auth_method": "HMAC"
  },
  "payload": {
    "side": "BUY",
    "qty": 10,
    "order_type": "MKT",
    "time_in_force": "DAY",
    "reason": "external-signal"
  }
}
```

## `ingress.errors.v1`
```json
{
  "ingress_event_id": "ing-20260303-000913",
  "error_code": "VALIDATION_ERROR",
  "error_message": "Missing required field: idempotency_key",
  "source_protocol": "API",
  "source_type": "TRADER_UI",
  "principal": {
    "principal_id": "trader_01",
    "principal_type": "USER",
    "auth_method": "JWT"
  },
  "raw_payload_ref": "raw-20260303-000913"
}
```

## `trade.events.routed.v1`
```json
{
  "trade_event_id": "tev-20260303-000912",
  "raw_event_id": "raw-20260303-000912",
  "source_type": "EXTERNAL_SYSTEM",
  "source_event_id": "tv-alert-778912",
  "source_system": "event-processor-service",
  "trigger_actor_id": null,
  "side": "BUY",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "reason": "external-signal",
  "metadata": {
    "route_policy_version": "route-2026.03.03.1",
    "ingress_event_id": "ing-20260303-000912"
  }
}
```

## `signals.generated.v1`
```json
{
  "trade_event_id": "tev-20260303-000912",
  "raw_event_id": "raw-20260303-000912",
  "source_type": "AGENT_RUNTIME",
  "source_event_id": "model-tick-771",
  "source_system": "agent-runtime-service",
  "origin_source_type": "EXTERNAL_SYSTEM",
  "origin_source_event_id": "tv-alert-778912",
  "side": "BUY",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "reason": "momentum_breakout"
}
```

Signal source rules (legacy event-mode compatibility):
1. Manual/external/UI ingress is emitted as `ingress.events.normalized.v1`, not direct risk signals.
2. Downstream event processor consumes normalized events and emits `trade.events.routed.v1`.
3. Legacy compatibility mode may emit `signals.generated.v1`; current target command path calls `RiskDecisionService.EvaluateSignal` over gRPC.
4. Event-driven signals set `source_type=AGENT_RUNTIME` and preserve lineage (`trade_event_id`, origin fields).
5. Consumers treat `idempotency_key` as canonical dedupe key regardless of source path.

## `risk.decisions.v1`
```json
{
  "decision": "ALLOW",
  "deny_reasons": [],
  "policy_version": "2026.03.03-1",
  "policy_rule_set": "prod-default",
  "constraints": {
    "max_qty": 100,
    "max_net_position": 100,
    "cooldown_seconds": 15
  }
}
```

## `orders.intents.v1`
```json
{
  "order_intent_id": "ord_9f1a...",
  "side": "BUY",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "status": "SUBMIT_PENDING",
  "submission_deadline": "2026-03-03T22:01:00Z"
}
```

## `orders.status.v1`
```json
{
  "order_intent_id": "ord_9f1a...",
  "broker_order_id": 120341,
  "perm_id": 902331221,
  "status": "SUBMITTED_ACKED",
  "remaining_qty": 10,
  "filled_qty": 0
}
```

## `fills.executed.v1`
```json
{
  "order_intent_id": "ord_9f1a...",
  "exec_id": "00012f1d.65f4...",
  "perm_id": 902331221,
  "fill_qty": 10,
  "fill_price": 61.22,
  "commission": 0.35
}
```

## `policy.evaluations.audit.v1`
```json
{
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

## `positions.updated.v1`
```json
{
  "net_qty": 10,
  "avg_cost": 61.22,
  "realized_pnl": 0.0,
  "unrealized_pnl": 2.4,
  "mark_price": 61.46
}
```

## `pnl.snapshots.v1`
```json
{
  "snapshot_id": "snap_...",
  "realized_pnl": 120.4,
  "unrealized_pnl": -35.2,
  "equity": 10084.3,
  "drawdown": 0.012
}
```

## `risk.events.v1`
```json
{
  "event_type": "DAILY_LOSS_LIMIT_REACHED",
  "severity": "HIGH",
  "message": "Agent exceeded daily loss threshold.",
  "context": {
    "threshold": 500,
    "current": 532.1
  }
}
```

## `system.alerts.v1`
```json
{
  "alert_type": "ORDER_STATUS_TIMEOUT",
  "severity": "CRITICAL",
  "message": "No broker status in 60 seconds.",
  "context": {
    "order_intent_id": "ord_9f1a..."
  }
}
```

## Partitioning and Ordering
- Partition key: `agent_id` for agent-scoped events.
- Fallback partition key for non-agent ingress events: `integration_id`.
- Ordering guarantee: only within same partition.
- Critical per-agent flows must use consistent keying.

## Delivery Semantics
- Producer: at-least-once with outbox reliability.
- Consumer: effectively-once via inbox dedupe + idempotent handlers.

## Consumer Compatibility Rules
1. Consumers must ignore unknown fields for the current topic version.
2. Producers must not remove or rename existing required fields in `v1`.
3. Enum evolution in `v1` is additive only.
4. Breaking behavior requires new topic version (`*.v2`) and dual-publish migration.

## Dead-Letter Strategy
- Each critical consumer has dedicated DLQ topic.
- DLQ messages must include original topic, partition, offset, and failure reason.

## Contract Change Policy
- Backward-compatible additions allowed in `v1`.
- Breaking change requires new topic/version (`*.v2`) and migration plan.

## Extensibility Checklist For New Topics
1. Define producer and expected consumer groups.
2. Define partition key and ordering boundary.
3. Define dedupe key and retry behavior.
4. Define DLQ topic and failure reason fields.
5. Add JSON schema under `docs/contracts/schemas/`.
6. Add end-to-end trace and lineage fields.
