# 04 Service Contract Catalog

## Canonical Service Names
Use these names consistently across architecture, contracts, and implementation tasks.

- `ingress-gateway-service`
- `event-processor-service`
- `agent-runtime-service`
- `risk-service`
- `order-service`
- `ibkr-connector-service`
- `performance-service`
- `monitoring-api`
- `dashboard-ui`

## Contract Summary by Service
| Service | Inbound | Outbound | Critical Guarantees |
|---|---|---|---|
| `ingress-gateway-service` | WebHook/API/gRPC/WebSocket submissions | `ingress.events.normalized.v1`, `ingress.errors.v1` | authn/authz + idempotency + raw persistence before publish |
| `event-processor-service` | `ingress.events.normalized.v1` | `trade.events.routed.v1`, `ingress.errors.v1` | deterministic routing + lineage preservation |
| `agent-runtime-service` | strategy runtime inputs + `trade.events.routed.v1` | gRPC `EvaluateSignal` to `risk-service` | idempotency key and trace context required on every signal command |
| `risk-service` | gRPC `EvaluateSignal` + OPA sidecar evaluation | gRPC `CreateOrderIntent`, `risk.events.v1`, `policy.evaluations.audit.v1` | policy fail-closed + schema-validated decisions + command idempotency |
| `order-service` | gRPC `CreateOrderIntent`, operator controls, `orders.status.v1` | gRPC `Submit/Cancel/Replace`, `system.alerts.v1` | timeout -> unknown -> freeze |
| `ibkr-connector-service` | gRPC submit/cancel/replace commands + IBKR callbacks | `orders.status.v1`, `fills.executed.v1`, alerts/events | single writer, callback dedupe |
| `performance-service` | status/fill topics | positions/PnL topics | deterministic projection updates |
| `monitoring-api` | event projections and control requests | REST + SSE | control/query plane; no new event-ingress ownership |
| `dashboard-ui` | REST + SSE | REST control requests only | no direct event/DB writes |

## REST Contract Standards
- `/api/v1` namespace.
- Structured success/error envelope with `trace_id`.
- Authorization by role for mutating actions.
- Pagination/filtering on list endpoints.
- Idempotency behavior defined for create/control mutations.
- Control mutations must persist actor-audit metadata.
- OpenAPI source maintained at `docs/contracts/monitoring-api.openapi.yaml`.
- Contract validation script `./scripts/validate-api-contracts.sh` must pass.
- Ingress REST/WebHook contract should reach OpenAPI parity in follow-up iteration.

## Event Contract Standards
- Envelope includes: `event_id`, `event_type`, `event_version`, `occurred_at`, `trace_id`.
- Trading events include: `agent_id`, `instrument_id`.
- Order lifecycle events include: `order_intent_id`.
- Dedupe keys are defined by topic (`idempotency_key`, `perm_id`, `exec_id`).
- Events must be replay-safe and idempotent at consumer boundaries.
- Version changes require backward-compatible migration plan.
- Trade-event-originated signals include lineage fields (`raw_event_id`, `trade_event_id`) and source identity fields.
- Policy decision audit events must include explainability fields (`policy_version`, `policy_rule_set`, `matched_rule_ids`, `deny_reasons`, `failure_mode`).

## gRPC Command Contract Standards
- Command APIs are internal only and versioned under `autotrading.command.v1`.
- Metadata must carry `trace_id`, `request_id`, `idempotency_key`, `principal_id`.
- Timeouts must be explicit per call and aligned with SLO budgets.
- Retries are allowed only for transient transport errors and must preserve idempotency key.
- gRPC command success does not remove downstream Kafka audit/event obligations.

## Service Discovery and Config Standards
- Runtime services resolve upstreams by Kubernetes `Service` DNS (no static pod IP defaults).
- Runtime config uses `ConfigMap` (non-secret) and `Secret` (sensitive values).
- Services must validate config changes and retain last-known-good state on invalid updates.
- Command-critical paths must fail closed when no healthy endpoints or valid critical config are available.

## Reliability Contract Standards
1. Status-deadline contract: first broker status <= 60 seconds or unknown+freeze.
2. Reconciliation contract: resume only after clean result + operator ack.
3. Single-writer contract: broker submission authority owned only by `ibkr-connector-service`.
4. Clock contract: service timestamps in UTC and bounded drift monitoring.
5. Intake contract: ingress submissions must be durably persisted as immutable raw ingress records before normalized publish.
6. Discovery contract: command-critical services must fail closed when no healthy endpoint is discoverable.
7. Policy contract: OPA output must pass schema validation; invalid responses must fail closed.
8. Policy rollout contract: production policy activation requires signed bundle + approval + rollback target.

## Breaking Change Process
1. Add new versioned endpoint/topic (`v2`).
2. Dual publish/consume window.
3. Consumer migration and validation.
4. Controlled deprecation of old version.

## Detailed Contract References
- [Service Contracts](../SERVICE_CONTRACTS.md)
- [Contracts Readiness and Extensibility](../CONTRACTS_READINESS_AND_EXTENSIBILITY.md)
- [API Specification](../API_SPEC.md)
- [Kafka Event Contracts](../KAFKA_EVENT_CONTRACTS.md)
- [Policy Bundle Contract](../contracts/policy-bundle-contract.md)
- [Policy Decision Audit Contract](../contracts/policy-decision-audit-contract.md)
- [Service Discovery and Config Contract](../contracts/service-discovery-and-config.md)
- [Internal Command Plane Proto](../contracts/protos/internal-command-plane.proto)
- [Monitoring API Contract](../contracts/monitoring-api.md)
- [Trading Best Practices Baseline](../TRADING_BEST_PRACTICES_BASELINE.md)
