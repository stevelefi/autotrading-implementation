# Trading System Design Targets

## Purpose
Define the business use cases this architecture is built to support, plus the guardrails that must always hold.

## Core Use Cases

### 1) Replicated Trades (Public Disclosures)
**Goal**
- Convert public disclosures (for example, politician stock trades) into controlled order intents.

**Must-Have Capabilities**
- Ingest trusted disclosure feeds through ingress APIs/webhooks.
- Normalize events into one canonical signal format.
- Apply replication controls (sizing, delays, instrument filters).
- Run pre-trade policy and risk checks before order creation.
- Preserve traceability from source disclosure to broker fill.

**Guardrails**
- Treat delayed/incomplete disclosures as first-class cases.
- Enforce account limits and operator controls at all times.
- Keep every replicated trade explainable and auditable.

### 2) External AI Signal Platform Integration
**Goal**
- Accept third-party AI trade signals without delegating execution authority.

**Must-Have Capabilities**
- Secure producer onboarding (auth, scope, schema, idempotency).
- Canonical normalization independent of provider protocol.
- Confidence and policy gating before order creation.
- Provider-level throttles, allowlists, and kill switches.
- Deterministic reject reasons for invalid or unsafe signals.

**Guardrails**
- External AI remains a signal source only.
- Final allow/deny decisions stay in risk and order services.
- Monitor provider quality with acceptance/deny telemetry.

## Account, Agent, and IBKR Model
This is the default interaction model for all use cases:

1. A user account controls one or more `agent_id` strategies.
2. Each `agent_id` is mapped to an IBKR account/profile (paper or prod).
3. Submissions must include `agent_id` and pass ingress authorization for that scope.
4. `risk-service` decides allow/deny; `order-service` owns order intent state.
5. `ibkr-connector-service` is the only service that talks to IBKR.
6. Connector correlation key is `order_ref={agent_id}:{order_intent_id}`.
7. Broker callbacks are normalized to `orders.status.v1` and `fills.executed.v1`.
8. Fills are accounted by `exec_id` dedupe; uncertain states trigger freeze + reconcile.

## Recommended Next Capabilities
- **Human-in-the-loop modes**: auto-approve, manual-approve, global/scoped freeze.
- **Paper-to-prod promotion**: soak period, explicit promotion gates, fast rollback.
- **Multi-provider and multi-broker adapters**: keep core contracts vendor-agnostic.
- **Explainability defaults**: reason codes, policy version, matched rules, lifecycle diagnostics.

## Global Design Guardrails
- No bypass around ingress, risk, or order boundaries.
- No unchecked autonomous execution path.
- No research/optimization workloads in production command path.

## Success Criteria
- Same signal semantics across Trader UI, API, webhook, and gRPC producers.
- Zero unsafe-allow outcomes when policy or execution dependencies fail.
- Full audit chain from inbound event to final broker outcome.
- New strategies/providers onboard with minimal core contract change.
