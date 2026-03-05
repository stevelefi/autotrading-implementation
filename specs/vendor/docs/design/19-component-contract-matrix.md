# 19 Component Contract Matrix

## Purpose
This document is the implementation handoff for component-to-component integration.
Each section defines one service pair with:
- channel and ownership,
- required fields and idempotency keys,
- timeout and retry behavior,
- request and response examples.

## Global Contract Rules
| Rule | Requirement |
|---|---|
| Correlation | Every interaction carries `trace_id` and `agent_id` when applicable |
| Command metadata | Internal gRPC commands carry `x-request-id`, `trace_id`, `idempotency_key`, and `principal_id` |
| Lifecycle anchor | `order_intent_id` is immutable after creation |
| Idempotency | Signals use `idempotency_key`; fills use `exec_id`; broker orders use `perm_id` when available |
| Time | All timestamps are UTC in ISO-8601 |
| Consistency safety | Unknown execution state triggers freeze before new intent creation |

## Common Event Envelope
```json
{
  "event_id": "evt-01JZ2QX1X8",
  "event_type": "signals.generated",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:02:15.104Z",
  "trace_id": "trc-7f2ad8f5",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART"
}
```

## CP-00: Ingress Gateway -> Event Processor
| Aspect | Contract |
|---|---|
| Channel | Kafka topic `ingress.events.normalized.v1` |
| Producer | `ingress-gateway-service` |
| Consumer | `event-processor-service` |
| Key | `agent_id` when present, otherwise `integration_id` |
| Required fields | `ingress_event_id`, `trace_id`, `idempotency_key`, `source_protocol`, `source_type`, `event_intent` |
| Validation | consumer rejects invalid intent/schema and emits `ingress.errors.v1` |
| Retry | ingress outbox retry + consumer inbox dedupe |
| Idempotency | no duplicate routed publish for same ingress key lineage |

Request example (`ingress.events.normalized.v1`):
```json
{
  "event_id": "evt-ing-1001",
  "event_type": "ingress.events.normalized",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:00:15.104Z",
  "trace_id": "trc-6f1ad8f5",
  "ingress_event_id": "ing-20260304-000912",
  "idempotency_key": "ext-tv-778912",
  "agent_id": "agent-meanrev-01",
  "source_protocol": "WEBHOOK",
  "source_type": "EXTERNAL_SYSTEM",
  "source_event_id": "tv-alert-778912",
  "payload": {
    "side": "BUY",
    "qty": 10,
    "order_type": "MKT",
    "time_in_force": "DAY"
  }
}
```

Response example (`trade.events.routed.v1`):
```json
{
  "event_id": "evt-route-2001",
  "event_type": "trade.events.routed",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:00:15.186Z",
  "trace_id": "trc-6f1ad8f5",
  "agent_id": "agent-meanrev-01",
  "trade_event_id": "tev-20260304-000912",
  "raw_event_id": "raw-20260304-000912",
  "idempotency_key": "ext-tv-778912"
}
```

## CP-01: Agent Runtime -> Risk Service
| Aspect | Contract |
|---|---|
| Channel | gRPC `RiskDecisionService.EvaluateSignal` |
| Producer | `agent-runtime-service` |
| Consumer | `risk-service` |
| Required fields | `request_context`, `signal_id`, `side`, `qty`, `strategy_ts`, `instrument_id`, `trade_event_id`, `origin_source_type`, `source_system` |
| Validation | `qty` and instrument whitelist validated before policy evaluation |
| Retry | caller retries transient transport errors with same `idempotency_key` |
| Idempotency | one effective risk evaluation per `idempotency_key` |

Request example (`EvaluateSignalRequest`):
```json
{
  "request_context": {
    "trace_id": "trc-7f2ad8f5",
    "request_id": "req-sig-1001",
    "idempotency_key": "agent-meanrev-01:20260304:142215:BUY:10",
    "principal_id": "svc-agent-runtime"
  },
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "signal_id": "sig-8b4e",
  "side": "BUY",
  "qty": 10,
  "strategy_ts": "2026-03-04T17:02:15.083Z"
}
```

Response example (`EvaluateSignalResponse`):
```json
{
  "trace_id": "trc-7f2ad8f5",
  "decision": "ALLOW",
  "policy_version": "risk-bundle-2026.03.04.01",
  "policy_rule_set": "prod-default",
  "matched_rule_ids": ["SESSION_WINDOW", "MAX_NET_POSITION"],
  "deny_reasons": [],
  "failure_mode": "NONE"
}
```

## CP-02: Risk Service -> Order Service
| Aspect | Contract |
|---|---|
| Channel | gRPC `OrderCommandService.CreateOrderIntent` |
| Producer | `risk-service` |
| Consumer | `order-service` |
| Required fields | `request_context`, `signal_id`, `decision`, `policy_version`, `policy_rule_set`, `matched_rule_ids`, `deny_reasons`, `failure_mode`, `side`, `qty` |
| Decision behavior | `DENY` means no order intent creation |
| Retry | bounded retry on transient errors only |
| Idempotency | one effective intent result per `signal_id` + `idempotency_key` |

Request example (`CreateOrderIntentRequest`):
```json
{
  "request_context": {
    "trace_id": "trc-c7d19f40",
    "request_id": "req-risk-1002",
    "idempotency_key": "sig-9f1d",
    "principal_id": "svc-risk"
  },
  "agent_id": "agent-breakout-02",
  "instrument_id": "MNQ.FUT.CME",
  "signal_id": "sig-9f1d",
  "decision": "DENY",
  "policy_version": "risk-bundle-2026.03.04.01",
  "policy_rule_set": "prod-default",
  "matched_rule_ids": ["DAILY_LOSS_CAP"],
  "deny_reasons": ["MAX_DAILY_LOSS_EXCEEDED"],
  "failure_mode": "NONE"
}
```

Response example (`CreateOrderIntentResponse`):
```json
{
  "trace_id": "trc-c7d19f40",
  "status": "REJECTED",
  "order_intent_id": "",
  "reasons": ["MAX_DAILY_LOSS_EXCEEDED"]
}
```

## CP-03: Order Service -> IBKR Connector
| Aspect | Contract |
|---|---|
| Channel | gRPC `BrokerCommandService.SubmitOrder` |
| Producer | `order-service` |
| Consumer | `ibkr-connector-service` |
| Required fields | `request_context`, `order_intent_id`, `side`, `qty`, `order_type`, `time_in_force` |
| Mapping key | `order_ref={agent_id}:{order_intent_id}` |
| Timeout | connector submit ack deadline enforced by caller |
| Idempotency | one effective submission per `order_intent_id` |

Request example (`SubmitOrderRequest`):
```json
{
  "request_context": {
    "trace_id": "trc-b0f40be2",
    "request_id": "req-order-2201",
    "idempotency_key": "oi-3abf2b",
    "principal_id": "svc-order"
  },
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "order_intent_id": "oi-3abf2b",
  "side": "BUY",
  "qty": 10,
  "order_type": "MKT",
  "time_in_force": "DAY",
  "submission_deadline_ms": 60000
}
```

Response example (`SubmitOrderResponse`):
```json
{
  "trace_id": "trc-b0f40be2",
  "status": "ACCEPTED",
  "broker_submit_id": "ib-submit-7145531",
  "submitted_at": "2026-03-04T17:04:11.402Z"
}
```

## CP-04: IBKR Connector -> Order Service
| Aspect | Contract |
|---|---|
| Channel | Kafka topic `orders.status.v1` |
| Producer | `ibkr-connector-service` |
| Consumer | `order-service` |
| Required fields | `order_intent_id`, `status`, broker identifiers when available |
| Dedupe keys | `perm_id`, `ib_order_id`, status transition guard |
| Escalation | if no status before deadline, `order-service` sets `UNKNOWN_PENDING_RECON` |
| Alerting | emit `system.alerts.v1` and freeze trading mode |

Request example (`orders.status.v1` partial fill):
```json
{
  "event_id": "evt-status-9002",
  "event_type": "orders.status",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:04:12.004Z",
  "trace_id": "trc-b0f40be2",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "payload": {
    "order_intent_id": "oi-3abf2b",
    "perm_id": 990882143,
    "status": "PARTIALLY_FILLED",
    "filled_qty": 6,
    "remaining_qty": 4
  }
}
```

Response example (`system.alerts.v1` timeout case):
```json
{
  "event_id": "evt-alert-4401",
  "event_type": "system.alerts",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:05:11.089Z",
  "trace_id": "trc-timeout-91",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "payload": {
    "severity": "CRITICAL",
    "alert_type": "ORDER_STATUS_TIMEOUT",
    "order_intent_id": "oi-3abf2b",
    "trading_mode": "FROZEN"
  }
}
```

## CP-05: IBKR Connector -> Performance Service
| Aspect | Contract |
|---|---|
| Channel | Kafka topic `fills.executed.v1` |
| Producer | `ibkr-connector-service` |
| Consumer | `performance-service` |
| Required fields | `exec_id`, `order_intent_id`, `fill_qty`, `fill_price`, `commission` |
| Idempotency | exactly-once effective by `exec_id` |
| Retry | consumer retry allowed; inbox dedupe must suppress duplicate effect |

Request example (`fills.executed.v1`):
```json
{
  "event_id": "evt-fill-7701",
  "event_type": "fills.executed",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:04:12.118Z",
  "trace_id": "trc-b0f40be2",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "payload": {
    "exec_id": "0001f1.65e0f7b4.01.01",
    "order_intent_id": "oi-3abf2b",
    "side": "BUY",
    "fill_qty": 6,
    "fill_price": 59.42,
    "commission": 0.32
  }
}
```

Response example (`positions.updated.v1`):
```json
{
  "event_id": "evt-pos-5101",
  "event_type": "positions.updated",
  "event_version": 1,
  "occurred_at": "2026-03-04T17:04:12.204Z",
  "trace_id": "trc-b0f40be2",
  "agent_id": "agent-meanrev-01",
  "instrument_id": "TQQQ.STK.SMART",
  "payload": {
    "net_qty": 26,
    "avg_cost": 58.97,
    "realized_pnl": 42.15,
    "unrealized_pnl": 11.28
  }
}
```

## CP-06: Performance Service -> Monitoring API Projection
| Aspect | Contract |
|---|---|
| Channel | Kafka topics `positions.updated.v1`, `pnl.snapshots.v1` |
| Producer | `performance-service` |
| Consumer | `monitoring-api` projection workers |
| Required fields | `agent_id`, `instrument_id`, `snapshot_ts`, pnl values |
| Ordering | key by `agent_id` to preserve per-agent ordering |
| Recovery | projection can be rebuilt from offsets and snapshots |

## CP-07: Dashboard UI -> Monitoring API (REST)
| Aspect | Contract |
|---|---|
| Channel | HTTPS REST (`monitoring-api`) |
| Producer | `dashboard-ui` |
| Consumer | `monitoring-api` |
| Mutating endpoints | kill switch, reconciliation start, reconciliation acknowledge |
| Read endpoints | positions, orders, performance, health |
| Auth and audit | actor identity required on mutation; `trace_id` returned in all responses |

Request example (`POST /api/v1/system/kill-switch`):
```http
POST /api/v1/system/kill-switch HTTP/1.1
Content-Type: application/json
X-Actor-Id: ops-user-17
X-Request-Id: req-1193

{
  "state": "ON",
  "reason": "manual freeze during reconciliation"
}
```

Response example:
```json
{
  "status": "ACCEPTED",
  "trading_mode": "FROZEN",
  "kill_switch": "ON",
  "trace_id": "trc-ops-1193",
  "updated_at": "2026-03-04T17:10:04.118Z"
}
```

## CP-08: Risk Service -> OPA Sidecar
| Aspect | Contract |
|---|---|
| Channel | Local HTTP (`POST /v1/data/trading/allow`) |
| Producer | `risk-service` |
| Consumer | OPA sidecar |
| Required input | signal, limits, current position, daily pnl, system mode, `schema_version=opa.policy.input.v1` |
| Failure mode | fail-closed deny with risk event |
| Versioning | response includes `schema_version=opa.policy.decision.v1` + `policy_version` |
| Reason taxonomy | `POLICY_DENY`, `OPA_TIMEOUT`, `OPA_UNAVAILABLE`, `OPA_SCHEMA_ERROR`, `BUNDLE_LOAD_ERROR` |

## CP-09: Monitoring API -> Dashboard UI (SSE)
| Aspect | Contract |
|---|---|
| Channel | `GET /api/v1/stream/events` (SSE) |
| Producer | `monitoring-api` |
| Consumer | `dashboard-ui` |
| Event types | `order.status`, `risk.event`, `position.update`, `pnl.snapshot`, `system.health`, `reconciliation.update` |
| Ordering | ordered per connection by projection publish order |
| Recovery | client reconnect uses `Last-Event-ID` |

SSE event example:
```text
event: order.status
id: sse-01JZ31M
data: {"agent_id":"agent-meanrev-01","order_intent_id":"oi-3abf2b","status":"FILLED","trace_id":"trc-b0f40be2"}
```

## CP-12: Risk Service -> Policy Evaluation Audit Topic
| Aspect | Contract |
|---|---|
| Channel | Kafka topic `policy.evaluations.audit.v1` |
| Producer | `risk-service` |
| Consumer | monitoring/compliance projection consumers |
| Required fields | `trace_id`, `agent_id`, `signal_id`, `decision`, `policy_version`, `matched_rule_ids`, `deny_reasons`, `failure_mode`, `latency_ms` |
| Idempotency | dedupe by `trace_id` + `signal_id` + `policy_version` |
| Failure behavior | publish failures retry via outbox; decision path remains fail-closed |

Request example (`policy.evaluations.audit.v1`):
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
  "matched_rule_ids": ["DAILY_LOSS_CAP"],
  "deny_reasons": ["DAILY_LOSS_LIMIT_REACHED"],
  "failure_mode": "NONE",
  "latency_ms": 8,
  "source_system": "risk-service"
}
```

## CP-10: Runtime Service -> Kubernetes Service Discovery
| Aspect | Contract |
|---|---|
| Channel | Kubernetes `Service` + cluster DNS |
| Producer | runtime services (`agent-runtime`, `risk`, `order`, `ibkr-connector`, `monitoring-api`) |
| Consumer | same runtime services as clients |
| Service contract | canonical service name, namespace, readiness/liveness probes |
| Resolution key | logical Kubernetes service name |
| Failure behavior | command-critical paths fail closed when no healthy endpoints are available |

Discovery example:
```json
{
  "service_name": "risk-service",
  "namespace": "paper",
  "dns_name": "risk-service.paper.svc.cluster.local",
  "port": 8081,
  "readiness": "healthy"
}
```

## CP-11: Runtime Service -> Kubernetes Runtime Config
| Aspect | Contract |
|---|---|
| Channel | Kubernetes `ConfigMap` / `Secret` read/watch |
| Producer | platform config owners |
| Consumer | runtime services |
| Resources | shared defaults config map + service config map + service secret |
| Initial keys | broker endpoints, policy bundle endpoint, feature flags |
| Load order | local defaults -> ConfigMap overrides -> Secret values -> controlled runtime reload |
| Failure behavior | invalid updates rejected; last-known-good retained; fail closed for invalid/missing critical config |

Runtime config example:
```json
{
  "configmap": "risk-service-config",
  "namespace": "paper",
  "key": "trading.risk.max-order-qty",
  "value": "100",
  "updated_at": "2026-03-04T18:00:00Z"
}
```

## Change Control Checklist for Pair Updates
1. Update pair section in this matrix.
2. Update source contract doc in `docs/contracts/`.
3. Update API/topic/schema/proto examples.
4. Add or update integration test case for this pair.
5. Link evidence in task tracker before moving task to Done.
