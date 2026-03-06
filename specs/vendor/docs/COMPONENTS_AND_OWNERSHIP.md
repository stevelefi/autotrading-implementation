# Components and Ownership

This document defines current runtime components, their roles, and ownership boundaries.

## Naming Conventions
- Internal runtime domains: `*-service`
- Public control/query edge: `*-api`
- User interface: `*-ui`

Canonical names used in this repo:
- `ingress-gateway-service`
- `event-processor-service`
- `agent-runtime-service`
- `risk-service`
- `order-service`
- `ibkr-connector-service`
- `performance-service`
- `monitoring-api`
- `dashboard-ui`

## Runtime Component Catalog
| Component | Type | Primary Role | Owns Authoritative State | Inbound Interfaces | Outbound Interfaces | Primary Team |
|---|---|---|---|---|---|---|
| `ingress-gateway-service` | service | Unified ingress boundary for external systems and Trader UI | immutable ingress raw events, ingress dedupe keys, ingress outbox rows | WebHook, REST, gRPC, WebSocket submissions | Kafka `ingress.events.normalized.v1`, `ingress.errors.v1` | API/UI |
| `event-processor-service` | service | Converts normalized ingress events into routed trade events | routing lineage metadata and outbox rows | Kafka `ingress.events.normalized.v1` | Kafka `trade.events.routed.v1`, `ingress.errors.v1` | Trading Core |
| `agent-runtime-service` | service | Runs strategies from routed events and requests risk decisions | routed-event consumer checkpoint, signal metadata, and outbox rows | strategy runtime events, Kafka `trade.events.routed.v1` | gRPC `EvaluateSignal` requests to `risk-service` | Trading Core |
| `risk-service` | service | Evaluates policy and risk limits | decision and risk-event records | gRPC `EvaluateSignal`, OPA evaluation | gRPC `CreateOrderIntent`, Kafka `risk.events.v1` | Trading Core + Policy Platform |
| `order-service` | service | Owns order state machine and deadlines | order ledger and lifecycle transitions | gRPC `CreateOrderIntent`, Kafka `orders.status.v1`, operator control commands | gRPC submit/cancel/replace to connector, Kafka `system.alerts.v1` | Trading Core |
| `ibkr-connector-service` | connector-service | Submits/cancels orders and normalizes broker callbacks | broker mapping (`order_ref`, `perm_id`, `exec_id`) | gRPC order commands, IBKR callbacks | Kafka `orders.status.v1`, `fills.executed.v1`, alerts/events | Broker Connectivity |
| `performance-service` | service | Projects fills and statuses to position/PnL state | positions and pnl snapshots | Kafka `fills.executed.v1`, `orders.status.v1` | Kafka `positions.updated.v1`, `pnl.snapshots.v1` | Data Platform |
| `monitoring-api` | api | Operator control/query API and SSE edge | read models and control/audit records | HTTPS REST + projection streams | REST responses, SSE events | API/UI |
| `dashboard-ui` | ui | Operator dashboard and action workflows | UI session state only | REST + SSE from `monitoring-api` | REST control commands to `monitoring-api` | API/UI |
| `opa-sidecar` | policy engine | Executes policy bundles for risk decisions | in-memory policy cache | local policy evaluation requests | local decision responses | Policy Platform |

## Service Role Details
### `ingress-gateway-service`
- Accepts events over WebHook/API/gRPC/WebSocket with protocol-specific authentication.
- Enforces schema and mandatory idempotency key before acceptance.
- Persists immutable raw ingress record before normalized publish.
- Emits canonical normalized events and diagnostic error events.

### `event-processor-service`
- Consumes canonical ingress normalized events.
- Validates routing intent and preserves lineage fields.
- Emits routed trade events for strategy/agent processing.

### `agent-runtime-service`
- Receives strategy/runtime triggers and routed trade events, then submits risk evaluation commands over gRPC.
- Enforces local strategy constraints before issuing command requests.
- Does not call broker APIs or mutate order lifecycle state.

### `risk-service`
- Evaluates each signal with static limits + dynamic OPA policy decisions.
- Produces explicit `ALLOW` or `DENY` outcomes with explainability metadata and calls order service over gRPC.
- Fails closed on policy errors/timeouts for opening orders.

### `order-service`
- Converts approved decisions to immutable `order_intent_id` records.
- Owns lifecycle transitions (`CREATED` to terminal states and unknown/reconcile states).
- Issues broker command requests to `ibkr-connector-service` over gRPC.
- Enforces 60-second broker-status deadline and triggers freeze controls on uncertainty.

### `ibkr-connector-service`
- Single active writer to IBKR.
- Submits/cancels/replaces broker orders via gRPC command calls and translates callbacks into normalized status/fill events.
- Dedupe authority for broker callback ids (`perm_id`, `exec_id`) before publish.

### `performance-service`
- Projects order/fill outcomes into positions and PnL snapshots.
- Guarantees exactly-once effective PnL update using fill dedupe keys.
- Feeds dashboard-facing read models through event streams.

### `monitoring-api`
- Only external operator control/query interface.
- Applies authZ + actor-audit requirements for every mutating command.
- Exposes REST for query/control and SSE for live events.
- Does not own new event ingress for Trader UI/external producers.

### `dashboard-ui`
- Presents health, orders, risk, and performance views to operators.
- Sends control actions only through `monitoring-api`.
- Never writes directly to Kafka or PostgreSQL.

### `opa-sidecar`
- Evaluates Rego bundles for dynamic risk rules (time windows, instrument policies, exceptions).
- Returns deterministic allow/deny decisions with policy version metadata.
- Isolated from broker/network side effects; decision engine only.

## Non-Negotiable Boundaries
1. `order-service` is the lifecycle authority for internal order states.
2. `ibkr-connector-service` is the only broker writer.
3. `ingress-gateway-service` is the only public event-ingress entry for new submissions.
4. `monitoring-api` is the only operator control/query entry point.
5. `dashboard-ui` does not publish Kafka events directly.
6. Cross-component mutations must flow through published contracts (REST/gRPC/Kafka), never direct DB writes.

## Ownership Matrix
| Domain | Primary Team | Backup Team |
|---|---|---|
| Ingress protocol and identity boundary | API/UI | SRE |
| Ingress-to-routing transformation | Trading Core | API/UI |
| Order state machine and freeze rules | Trading Core | Broker Connectivity |
| IBKR submission/callback normalization | Broker Connectivity | Trading Core |
| OPA policy lifecycle and rollout | Policy Platform | Trading Core |
| Schema and migration lifecycle | Data Platform | Trading Core |
| Kafka contract lifecycle | Data Platform | Trading Core |
| Operator API and dashboard UX | API/UI | SRE |
| Observability and incident readiness | SRE | API/UI |
| Task traceability automation | Platform DevEx | QA/Release |

## Handoff Checklist For Any Component Change
1. Update responsibilities and ownership in this document.
2. Update service contract under `docs/contracts/`.
3. Update API/Kafka contract docs as applicable.
4. Update runbook and alert mappings if operational behavior changes.
