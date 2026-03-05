# Kubernetes Config and Secret Ownership Model

## Purpose
Define how to split configuration and secret ownership between service repositories and a centralized platform GitOps repository.

## Decision
Use a hybrid model:
1. Service repositories own config contract and defaults.
2. Central GitOps repository owns environment-specific runtime values and rollout sequencing.
3. Secret values live in a secret manager, not in Git.

## Why This Model
1. Service teams control schema and validation close to code.
2. Platform/SRE controls environment safety and promotion flow.
3. Compliance risk is reduced because secret values are not committed to application repos.

## Ownership Split
| Artifact | Owner | Repo |
|---|---|---|
| config key definitions and defaults | service team | service repo |
| typed validation (`@ConfigurationProperties`) | service team | service repo |
| Helm/Kustomize base template | service team | service repo |
| paper/prod value overlays | platform + env owners | GitOps repo |
| secret references (`ExternalSecret`/secret mapping) | platform + security | GitOps repo |
| raw secret values | security/runtime operators | secret manager only |

## Repository Structure
## 1) Service Repository (example: `risk-service`)
```text
risk-service/
  src/main/resources/
    application.yml
    application-local.yml
  src/main/java/.../config/
    RiskConfig.java
  deploy/
    helm/
      risk-service/
        Chart.yaml
        values.yaml
        values.schema.json
        templates/
          deployment.yaml
          service.yaml
          configmap.yaml
          externalsecret-ref.yaml
  docs/
    CONFIG_CONTRACT.md
  .env.example
```

Service repo rules:
1. Include safe defaults only.
2. Document all required keys and validation rules.
3. Do not include production secret values.

## 2) Platform GitOps Repository (example: `trading-platform-gitops`)
```text
trading-platform-gitops/
  clusters/
    paper/
      apps/
        risk-service/
          helmrelease.yaml
          values-risk-service.yaml
          externalsecret-risk-service.yaml
    prod/
      apps/
        risk-service/
          helmrelease.yaml
          values-risk-service.yaml
          externalsecret-risk-service.yaml
  policies/
    rbac/
    network/
  docs/
    PROMOTION_WORKFLOW.md
```

GitOps repo rules:
1. Holds environment values and release orchestration.
2. Holds secret references only (not secret plaintext).
3. Enforces review/approval gates for `prod`.

## Secret Management Pattern
Recommended:
1. Store secrets in cloud secret manager (or Vault).
2. Use `ExternalSecret` (or equivalent) to sync into namespace `Secret`.
3. App reads only Kubernetes `Secret`.

Example flow:
1. Security rotates secret in manager.
2. External secret controller syncs to K8s.
3. Deployment picks up secret via restart/rollout policy.

## Change Workflow
## A) Add a new non-secret config key
1. Service team updates:
   - `application.yml` default,
   - typed config class + validation,
   - `CONFIG_CONTRACT.md`.
2. Service team updates chart schema (`values.schema.json`).
3. Platform team adds env values in GitOps overlays (`paper` then `prod`).
4. Validate in `paper`.
5. Promote to `prod` with approval.

## B) Add a new secret key
1. Service team documents required secret name/key.
2. Security/platform adds value to secret manager.
3. Platform team updates `ExternalSecret` mapping in GitOps repo.
4. Validate in `paper` namespace.
5. Promote to `prod` with approval.

## PR and Approval Policy
1. Service repo PR reviewers:
   - service owner + one platform reviewer for config contract changes.
2. GitOps repo PR reviewers:
   - platform/SRE + environment owner.
3. Production secret mapping changes require security reviewer.

## Runtime Safety Rules
1. Missing required secret/config on startup => readiness false.
2. Invalid config update => reject update and keep last-known-good.
3. No healthy endpoint or invalid critical config on command path => fail closed.

## Anti-Patterns (Do Not Do)
1. Put secret plaintext in service repo or GitOps repo.
2. Let each service keep separate, uncontrolled environment overlays.
3. Use different key names across local/paper/prod.
4. Bypass typed validation and read raw maps everywhere.

## Onboarding Checklist for a New Service
1. Define config and secret contract in service repo.
2. Add typed validation and safe defaults.
3. Add chart templates and schema.
4. Add GitOps overlays for `paper` and `prod`.
5. Add `ExternalSecret` mapping if secrets are needed.
6. Run paper validation before production enablement.

## Related Documents
- [Spring Boot + Kubernetes Config Guide](./SPRINGBOOT_K8S_CONFIG_GUIDE.md)
- [Service Discovery and Config Contract](./contracts/service-discovery-and-config.md)
- [Production Plan](./PRODUCTION_PLAN.md)
