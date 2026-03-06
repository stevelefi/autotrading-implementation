# 4-Day Solo + AI DevOps Blitz Playbook

This playbook is the execution guide when one senior engineer owns both implementation and DevOps with AI assistance.

## Goal
Deliver one paper-trading vertical slice in 4 days with reliability controls:
1. shared envelope propagation
2. idempotency boundaries
3. outbox/inbox reliability
4. compose runtime + CI + Helm validation

## Locked Decisions
1. Team model: solo engineer + AI parallel streams.
2. Timeline: 4-day blitz.
3. Outcome: paper vertical slice, not production rollout.
4. Runtime for sprint: Docker Compose.
5. Kubernetes packaging: Helm-first.
6. Local secrets: `.env.local` + `.env.example`.
7. Shared reliability libs: one module now, split later only if needed.

## Monorepo Blueprint (Implementation Repo)
1. `libs/reliability-core`
2. `services/ingress-gateway-service`
3. `services/event-processor-service`
4. `services/agent-runtime-service`
5. `services/risk-service`
6. `services/order-service`
7. `services/ibkr-connector-service` (sim mode)
8. `services/performance-service`
9. `services/monitoring-api`
10. `db/migrations`
11. `infra/local`
12. `infra/helm`
13. `tests/e2e`
14. `docs/runbooks`

## Ownership Model
One human owner (you), four AI streams:
1. Stream A: service contracts + envelope propagation
2. Stream B: idempotency + migrations
3. Stream C: outbox/inbox + messaging workers
4. Stream D: DevOps track (compose, helm, CI, e2e, evidence pack)
5. Human gate: approve every merge to integration branch.

## Scope Lock
In scope:
1. paper vertical slice only
2. Java 21 + Spring Boot runtime baseline
3. Docker Compose for local runtime
4. Helm-first packaging validation
5. `.env.local` and templates for secrets

Out of scope:
1. production rollout
2. full UI polish
3. multi-broker support
4. full GitOps promotion automation

## Reliability Library Structure (Java)
Module:
1. `libs/reliability-core`

Packages:
1. `com.autotrading.libs.commonenvelope`
2. `com.autotrading.libs.idempotency`
3. `com.autotrading.libs.reliability.outbox`
4. `com.autotrading.libs.reliability.inbox`

Rule:
1. public interfaces stable from day 1
2. internal implementation can evolve during sprint

## Day 1: Platform Bootstrap
Deliverables:
1. implementation monorepo skeleton
2. `infra/local/docker-compose.yml`
3. `.env.example`, `infra/local/.env.compose.example`, `.gitignore` rules for `.env.local`
4. `Makefile` targets:
- `make up`
- `make down`
- `make logs`
- `make test-unit`
- `make test-e2e`
5. CI workflow `ci.yml` with build + unit + migration checks

Exit criteria:
1. stack boots with healthy services
2. migrations apply
3. CI green on skeleton

## Day 2: Reliability and Messaging
Deliverables:
1. `libs/reliability-core` package boundaries:
- `com.autotrading.libs.commonenvelope`
- `com.autotrading.libs.idempotency`
- `com.autotrading.libs.reliability.outbox`
- `com.autotrading.libs.reliability.inbox`
2. idempotency persistence (`idempotency_records`)
3. outbox/inbox tables and workers
4. compose smoke job in CI

Exit criteria:
1. duplicate ingress key does not duplicate routed event
2. outbox rows publish after commit
3. inbox dedupe suppresses duplicates

## Day 3: Command Path and Release Safety
Deliverables:
1. gRPC command chain active in paper mode
2. timeout/freeze safety path wired
3. Helm chart template:
- `infra/helm/charts/trading-service`
- service values files
4. CI workflow `helm-validate.yml`:
- `helm lint`
- `helm template` for paper profile
5. rollback primitive:
- `make rollback-local`

Exit criteria:
1. one BUY flow reaches simulated fill
2. timeout path triggers deterministic freeze
3. helm checks pass

## Day 4: Hardening and Evidence
Deliverables:
1. reliability drills:
- Kafka outage and recovery
- restart with pending outbox
- duplicate callback replay
2. metrics baseline:
- outbox backlog age
- duplicate suppression count
- first-status timeout count
3. evidence bundle:
- test report
- drill logs
- unresolved risks + next sprint backlog
4. implementation checkpoint tag:
- `impl-v0.1.0-paper-slice`

Exit criteria:
1. mandatory scenarios pass
2. evidence bundle complete
3. clean-clone demo reproducible

## DevOps CI/CD Contract (Minimum)
Required checks:
1. compile + unit tests
2. migration apply test
3. compose smoke
4. helm lint/template
5. spec drift verify against pinned ref

Branch policy:
1. protected `main`
2. stream branches `stream/a-*`, `stream/b-*`, `stream/c-*`, `stream/d-*`
3. integration branch `blitz/integration`
4. no direct merge to `main` before all checks pass

## Runtime Config and Secrets
Local:
1. `.env.local` for secrets
2. `.env.example` and `infra/local/.env.compose.example` as committed templates only

Kubernetes packaging shape:
1. ConfigMap for non-secret values
2. Secret references for sensitive values

Fail-closed defaults:
1. missing critical config => readiness false
2. invalid critical config => reject and alert

## Important Interface and Type Additions
Shared context required across all boundaries:
1. `trace_id`
2. `request_id`
3. `idempotency_key`
4. `principal_id`

Idempotency API contract:
1. `claim`
2. `markCompleted`
3. `markFailed`

Outbox/Inbox API contract:
1. `append` (same transaction as domain mutation)
2. `dispatchBatch`
3. `tryBegin` (consumer dedupe)

Required database tables:
1. `idempotency_records`
2. `outbox_events`
3. `consumer_inbox`

## Local Runtime Contract
Compose baseline services:
1. `postgres`
2. `kafka` (single-node dev profile)
3. `ibkr-sim`
4. vertical-slice services only

Readiness rules:
1. critical config missing => readiness false
2. invalid critical config => fail closed + alert
3. no healthy dependency on command path => reject command safely

## Secret and Config Policy
1. commit only templates (`.env.example`)
2. never commit real secrets
3. local secrets in `.env.local`
4. K8s packaging uses ConfigMap/Secret references

## Mandatory Scenario Checklist
1. same key + same payload => replay
2. same key + different payload => conflict
3. no outbox row without committed mutation
4. duplicate consumed event => no duplicate effect
5. gRPC retry with same key => no duplicate submit
6. missing first status within 60s => unknown/freeze
7. outbox backlog drains after recovery
8. restart preserves consistency

## End-of-Day-4 Deliverables
1. Working paper vertical-slice demo.
2. `libs/reliability-core` integrated in critical paths.
3. Compose runtime and operator commands documented.
4. Helm charts pass lint/template.
5. CI checks enforced for sprint scope.
6. Evidence pack archived under `reports/blitz/`.

## Evidence Pack Structure
```text
reports/blitz/
  day1-bootstrap.md
  day2-reliability.md
  day3-command-safety.md
  day4-hardening.md
  e2e-results/
  drill-logs/
  known-risks.md
```

## Assumptions and Defaults
1. Final reviewer and release authority is the solo engineer.
2. Baseline stack is Java 21 + Spring Boot + Postgres + Kafka + gRPC.
3. Broker mode is simulator for sprint acceptance.
4. Spec repo remains authoritative and implementation repos pin spec tag.
5. Sprint target is correctness and operability, not production readiness.

## References
1. [Implementation Phases and Team Plan](./IMPLEMENTATION_PHASES.md)
2. [Deliverables and Milestones Plan](./DELIVERABLES_AND_MILESTONES.md)
3. [Deployment and Environments](./DEPLOYMENT_AND_ENVIRONMENTS.md)
4. [Spring Boot + Kubernetes Config Guide](./SPRINGBOOT_K8S_CONFIG_GUIDE.md)
5. [Testing and Release Gates](./TESTING_AND_RELEASE_GATES.md)
6. [Observability and Alerting](./OBSERVABILITY_AND_ALERTING.md)
