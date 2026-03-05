# Contracts Readiness and Extensibility

This document is the high-level audit and extension standard for all APIs, topics, and service contracts.
It defines what is already covered, what is partial, and what every new capability must follow.

## 1) Current Readiness Snapshot

Status key:
- `READY`: documented and machine-checkable.
- `PARTIAL`: documented but missing one or more enforceable artifacts.
- `GAP`: missing required baseline artifact.

### API Surface
| API/Protocol Surface | Primary Contract | Machine Artifact | Status | Notes |
|---|---|---|---|---|
| Monitoring REST (`/api/v1/...`) | `docs/API_SPEC.md`, `docs/contracts/monitoring-api.md` | `docs/contracts/monitoring-api.openapi.yaml` | READY | Endpoints validated by `./scripts/validate-api-contracts.sh`. |
| Ingress REST (`/ingress/v1/events`) | `docs/contracts/ingress-gateway-service.md` | none | PARTIAL | Human contract exists; OpenAPI artifact should be added for parity. |
| Ingress WebHook (`/ingress/v1/webhooks/{integrationId}`) | `docs/contracts/ingress-gateway-service.md` | none | PARTIAL | Signature and replay rules documented; machine schema for endpoint contract is missing. |
| Ingress gRPC (`PublishEvent`) | `docs/contracts/ingress-gateway-service.md` | `docs/contracts/ingress-gateway-service.proto` | READY | Proto exists and is versioned under `v1`. |
| Ingress WebSocket (`/ingress/v1/ws`) | `docs/contracts/ingress-gateway-service.md` | `docs/contracts/schemas/ingress.ws.events.v1.json` | READY | Message types and error model are defined. |

### Event Topic Surface
| Topic | Contract Source | JSON Schema | Status | Notes |
|---|---|---|---|---|
| `ingress.events.normalized.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | `docs/contracts/schemas/ingress.events.normalized.v1.json` | READY | Canonical ingress output contract. |
| `policy.evaluations.audit.v1` | `docs/KAFKA_EVENT_CONTRACTS.md`, `docs/contracts/policy-decision-audit-contract.md` | `docs/contracts/schemas/policy.evaluations.audit.v1.json` | READY | Policy explainability and compliance audit stream. |
| `ingress.errors.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Example payload exists; schema file should be added. |
| `trade.events.routed.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | High-level contract exists; machine schema should be added. |
| `signals.generated.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Lineage defined; schema file should be added. |
| `risk.decisions.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Decision shape documented; schema file should be added. |
| `orders.intents.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Domain fields documented; schema file should be added. |
| `orders.status.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Broker mapping fields documented; schema file should be added. |
| `fills.executed.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Dedupe key (`exec_id`) defined; schema file should be added. |
| `positions.updated.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Projection shape documented; schema file should be added. |
| `pnl.snapshots.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Snapshot semantics documented; schema file should be added. |
| `risk.events.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Diagnostic event format documented; schema file should be added. |
| `system.alerts.v1` | `docs/KAFKA_EVENT_CONTRACTS.md` | none | PARTIAL | Alert semantics documented; schema file should be added. |

### Policy Schema Surface
| Schema | Contract Source | Artifact | Status | Notes |
|---|---|---|---|---|
| OPA input schema | `docs/contracts/risk-service.md`, `docs/RULE_ENGINE_OPA.md` | `docs/contracts/schemas/opa.policy.input.v1.json` | READY | Versioned request contract from `risk-service` to OPA. |
| OPA decision schema | `docs/contracts/risk-service.md`, `docs/RULE_ENGINE_OPA.md` | `docs/contracts/schemas/opa.policy.decision.v1.json` | READY | Versioned decision contract from OPA to `risk-service`. |

### Service Contract Surface
| Service | Contract File | Inbound/Outbound | Idempotency + Failure Rules | Status |
|---|---|---|---|---|
| `ingress-gateway-service` | `docs/contracts/ingress-gateway-service.md` | yes | yes | READY |
| `event-processor-service` | `docs/contracts/event-processor-service.md` | yes | yes | READY |
| `agent-runtime-service` | `docs/contracts/agent-runtime.md` | yes | yes | READY |
| `risk-service` | `docs/contracts/risk-service.md` | yes | yes | READY |
| `opa-sidecar` policy path | `docs/RULE_ENGINE_OPA.md`, `docs/contracts/policy-bundle-contract.md` | yes | yes | READY |
| `order-service` | `docs/contracts/order-service.md` | yes | yes | READY |
| `ibkr-connector-service` | `docs/contracts/ibkr-connector-service.md` | yes | yes | READY |
| `performance-service` | `docs/contracts/performance-service.md` | yes | yes | READY |
| `monitoring-api` | `docs/contracts/monitoring-api.md` | yes | yes | READY |

## 2) Extensibility Best Practices (Required)

### A. API Contract Standard
1. Every public endpoint must have:
   - human-readable contract page,
   - machine-readable OpenAPI or protobuf artifact,
   - stable error code catalog.
2. Mutations must require `X-Request-Id` and define idempotency behavior.
3. Every success/error response must carry `trace_id`.
4. Breaking changes require `v2` path/namespace and dual-run migration window.
5. Deprecated endpoints must include:
   - replacement endpoint,
   - deprecation date,
   - removal target milestone.

### B. Event Contract Standard
1. Every topic must define:
   - partition key policy,
   - ordering scope,
   - dedupe key,
   - retry/DLQ behavior.
2. Every topic must have a JSON schema under `docs/contracts/schemas/`.
3. Envelopes must preserve lineage (`trace_id`, upstream IDs, source identity fields).
4. Additive changes are allowed in `v1`; breaking changes require `*.v2`.
5. Consumer compatibility rule:
   - producers do not remove/rename existing required fields in the active version.

### C. Service Contract Standard
1. Each service contract must include:
   - owner team,
   - responsibilities,
   - inbound and outbound interfaces,
   - idempotency/consistency guarantees,
   - failure behavior,
   - SLO target.
2. Cross-service identity and trace fields must align with `docs/contracts/common.md`.
3. Any new service must publish a contract before implementation starts.

## 3) Extension Playbook

### Add a new API endpoint
1. Add endpoint to `docs/API_SPEC.md` or ingress API spec.
2. Update machine artifact (`openapi.yaml` or `.proto`).
3. Add success + error examples.
4. Add idempotency and auth rules.
5. Add validation test to CI scripts if endpoint family is new.

### Add a new topic
1. Add topic to `docs/KAFKA_EVENT_CONTRACTS.md`.
2. Add JSON schema file under `docs/contracts/schemas/`.
3. Define producer, consumers, partition key, dedupe key, and DLQ topic.
4. Define replay behavior and migration plan.

### Add a new service
1. Add service contract page under `docs/contracts/`.
2. Register service in `docs/source-of-truth/04-service-contract-catalog.md`.
3. Link service to topic/API contracts and ownership table in production plan.
4. Add SLO and alerting expectations before implementation milestone starts.

## 4) Priority Backlog To Reach Full Contract Coverage
1. Add OpenAPI for ingress REST/WebHook endpoints.
2. Add JSON schemas for all non-ingress Kafka topics.
3. Add a single contract validation script that checks:
   - OpenAPI parsing,
   - JSON schema parsing,
   - topic-to-schema mapping completeness,
   - gRPC proto-to-doc parity for required fields/enums on command APIs.
4. Execute deprecation/removal plan for legacy `monitoring-api` trade-intake endpoints after migration validation window.
5. Add automated schema-validation checks for `opa.policy.input.v1` and `opa.policy.decision.v1` contracts.

## Related References
- [Service Contracts](./SERVICE_CONTRACTS.md)
- [API Specification](./API_SPEC.md)
- [Kafka Event Contracts](./KAFKA_EVENT_CONTRACTS.md)
- [Common Contract Conventions](./contracts/common.md)
- [Source of Truth: Service Contract Catalog](./source-of-truth/04-service-contract-catalog.md)
