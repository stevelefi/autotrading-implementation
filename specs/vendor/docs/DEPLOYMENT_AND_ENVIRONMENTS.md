# Deployment and Environments

## Environments
- `local`: developer workstation for docs/plan tooling.
- `paper`: IBKR paper account, pre-live validation.
- `prod`: live trading account, controlled rollout.

This repository is planning/docs focused. Runtime service code, deploy manifests,
and environment automation are maintained in dedicated runtime repositories.

## Local Broker Simulation
Local broker simulation remains part of runtime validation strategy, but simulator
implementation artifacts are out of scope for this repository.
Reference contract behavior: [IBKR Gateway Simulator (DEV)](./IBKR_GATEWAY_SIMULATOR.md)

## Baseline Runtime
- Kubernetes cluster profile for paper and production runtime.
- One active `ibkr-connector-service` instance.
- Kafka KRaft single broker in early phase; move to replicated cluster later.
- Service discovery via Kubernetes `Service` + DNS.
- Runtime config via `ConfigMap` and `Secret`.

## Runtime Workloads (Logical)
- `postgres`
- `kafka`
- `ib-gateway`
- `agent-runtime-service`
- `risk-service`
- `order-service`
- `ibkr-connector-service`
- `performance-service`
- `monitoring-api`
- `dashboard-ui`
- `prometheus`
- `grafana`
- `loki`
- `alertmanager`

## Secrets and Config
- Store runtime secrets in Kubernetes `Secret` (or external secret manager integration).
- Never commit token or account credentials.
- Non-secret runtime values in versioned config files and `ConfigMap` resources.

## Upgrade Pattern
1. Freeze new opening orders if needed.
2. Deploy non-breaking schema changes.
3. Rolling service restart (connector last).
4. Run post-deploy health checks.
5. Resume trading mode if healthy.
