# Spring Boot + Kubernetes Config Guide

## Purpose
Define the team-standard way to run Spring Boot configuration on Kubernetes using native discovery and config resources.

## Decision Summary
1. Service discovery uses Kubernetes `Service` + cluster DNS.
2. Non-secret runtime config uses `ConfigMap`.
3. Sensitive runtime values use `Secret`.
4. Local `application.yml` keeps safe defaults.
5. Command-critical behavior fails closed on unsafe discovery/config state.

## Scope
Applies to:
- `agent-runtime-service`
- `risk-service`
- `order-service`
- `ibkr-connector-service`
- `monitoring-api`

## Runtime Model
1. Spring Boot service starts with local defaults.
2. Kubernetes `ConfigMap`/`Secret` overlays environment-specific values.
3. Pod readiness remains false until critical config is loaded and validated.
4. Runtime reload is allowed only for non-critical settings.
5. Critical setting changes use controlled rollout/restart.

## Resource Naming Convention
Per environment namespace (`paper`, `prod`):
1. Shared defaults:
   - `ConfigMap`: `trading-platform-application-config`
2. Service config:
   - `ConfigMap`: `{service-name}-config`
3. Service secrets:
   - `Secret`: `{service-name}-secret`
4. Optional feature flags:
   - `ConfigMap`: `trading-platform-feature-flags`

## Spring Boot Baseline
Use canonical service name:
```yaml
spring:
  application:
    name: risk-service
  config:
    import: "optional:kubernetes:"
```

Recommended Kubernetes integration:
```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        enabled: true
      config:
        enabled: true
        sources:
          - name: trading-platform-application-config
          - name: risk-service-config
      secrets:
        enabled: true
        sources:
          - name: risk-service-secret
```

## Typed Config and Validation (Required)
Use typed properties with validation:
```java
@Validated
@ConfigurationProperties(prefix = "trading.risk")
public record RiskConfig(
    @Min(1) int maxOrderQty,
    @Min(1) int maxNetPosition,
    @DecimalMin("0.0") BigDecimal maxDailyLoss
) {}
```

Validation policy:
1. Reject invalid updated values.
2. Keep last-known-good config in memory.
3. Emit structured log with `trace_id`, `service_name`, and key.

## Runtime Reload Guidance
1. Non-critical settings:
   - allow dynamic reload/watch and validation.
2. Critical trading controls:
   - apply via rolling restart only.
3. Environment variable based config:
   - requires pod restart to apply.
4. Volume-mounted config files:
   - may update without restart, but still require validation and safe-apply logic.

## Secrets Policy
1. Never place credentials/tokens in `ConfigMap`.
2. Store secrets in Kubernetes `Secret` (or external secret manager integration).
3. Restrict secret read access via namespace RBAC and service accounts.
4. Enable secret encryption at rest.

## Fail-Closed Rules (Command-Critical Path)
1. DNS/service discovery failure:
   - reject command and trigger safety controls.
2. Missing/invalid critical config:
   - do not mark service ready; block command handling.
3. Config reload failure:
   - keep last-known-good config and alert.

## Operational Checklist
Before production rollout:
1. `spring.application.name` matches canonical service name.
2. Service has readiness/liveness probes.
3. Config is split correctly (`ConfigMap` vs `Secret`).
4. Typed validation exists for critical properties.
5. Invalid config tests are verified (reject + retain last-known-good).
6. Fail-closed behavior is tested for discovery/config outages.

## Local Validation Commands
Inspect config map:
```bash
kubectl -n paper get configmap risk-service-config -o yaml
```

Inspect secret metadata:
```bash
kubectl -n paper get secret risk-service-secret
```

Check service endpoints:
```bash
kubectl -n paper get endpoints risk-service
```

## Related Documents
- [Service Discovery and Config Contract](./contracts/service-discovery-and-config.md)
- [Kubernetes Config and Secret Ownership Model](./K8S_CONFIG_OWNERSHIP_MODEL.md)
- [Cross-Service SLO and Error Budget](./contracts/cross-service-slo.md)
- [Reference Architecture](./source-of-truth/02-reference-architecture.md)
