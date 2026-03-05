# Service Discovery and Config Contract (Kubernetes-Native)

## Owner Team
SRE + Platform DevEx

## Responsibilities
- Provide service discovery for internal runtime traffic using Kubernetes `Service` + cluster DNS.
- Provide runtime configuration through `ConfigMap` and `Secret`.
- Enforce health-aware routing via readiness/liveness probes.
- Enforce fail-closed behavior when discovery/config become unsafe on command-critical paths.

## Scope
This contract applies to runtime service-to-service traffic and runtime config for:
- `agent-runtime-service`
- `risk-service`
- `order-service`
- `ibkr-connector-service`
- `monitoring-api`

Ingress protocols and public API contracts remain unchanged and are documented separately.

## Topology
1. Runtime services run in Kubernetes namespaces by environment (`paper`, `prod`).
2. Discovery is provided by Kubernetes `Service` objects and cluster DNS.
3. Runtime config is provided by `ConfigMap` (non-secret) and `Secret` (sensitive values).

Availability targets by phase:
- `paper`: 99.5% discovery/config-plane target.
- `prod`: 99.95% discovery/config-plane target.

## Service Registration Contract
Each runtime service instance must expose:
- canonical `service_name` from docs catalog,
- Kubernetes `Service` endpoint for client resolution,
- readiness/liveness probes,
- environment and version labels.

Requirements:
1. Pods must not receive traffic until readiness is true.
2. Readiness checks must reflect downstream dependency readiness.
3. Unhealthy pods must be removed from service endpoints.

## Discovery Behavior Contract
1. Clients resolve upstreams by Kubernetes DNS service name only.
2. No static pod IPs in production configuration.
3. Client libraries use app-level load balancing/retries across healthy service endpoints.
4. If no healthy endpoint is discoverable for command-critical path, caller fails closed.

## Config Contract (`ConfigMap` + `Secret`)
Required resources per environment:
1. Shared defaults `ConfigMap` (for cross-service defaults).
2. Service-specific `ConfigMap` (per runtime service).
3. Service-specific `Secret` for sensitive values only.
4. Optional feature-flag `ConfigMap` for controlled runtime toggles.

Load order:
1. Local static defaults.
2. `ConfigMap` overrides.
3. `Secret` values (for sensitive keys).
4. Runtime watch/reload for non-critical settings.

Validation rules:
1. Invalid config values must be rejected and logged with `trace_id`.
2. Last valid config remains active when new value fails validation.
3. Command-critical flags default to safest mode (`false` or blocked behavior).
4. Critical config changes should use controlled rollout/restart instead of best-effort hot reload.

## Security Model
1. Namespace-scoped RBAC for service accounts.
2. `Secret` resources for credentials/tokens only; no secrets in `ConfigMap`.
3. Secret encryption at rest enabled in cluster control plane.
4. Network policies segment command-critical traffic.

## Failure and Recovery Behavior
1. Discovery degradation (`Service`/DNS unavailable):
   - reject command-critical requests,
   - trigger fail-closed safeguards.
2. Config watch/apply failure:
   - keep last-known-good configuration,
   - emit warning metric/event.
3. Invalid config rollout:
   - reject application of invalid config,
   - retain prior valid state and alert.

## Observability Requirements
Required metrics:
- `k8s_dns_lookup_latency_ms`
- `k8s_dns_lookup_error_rate`
- `k8s_endpoint_unavailable_count`
- `config_apply_error_rate`
- `config_reload_lag_ms`
- `stale_config_age_seconds`

Required logs:
- readiness/liveness probe failures with pod identity
- DNS/service resolution failures with target service name
- config apply/reject events with resource name/key

## Related Contracts
- [Internal Command Plane Proto](./protos/internal-command-plane.proto)
- [Cross-Service SLO and Error Budget](./cross-service-slo.md)
- [Reference Architecture](../source-of-truth/02-reference-architecture.md)
- [Spring Boot + Kubernetes Config Guide](../SPRINGBOOT_K8S_CONFIG_GUIDE.md)
