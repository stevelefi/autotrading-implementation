# Deployment Pipeline (Tag-Driven + Fully GitOps + Manual Prod Approval)

This repository now includes [.github/workflows/deploy-main.yml](../.github/workflows/deploy-main.yml) with the following policy:

1. Only `push` tags matching `v*` can start a deployment workflow.
2. Tag must point to a commit reachable from `main`.
3. Workflow resolves one immutable image digest from the release tag, updates GitOps values for QA, commits to `main`, and ArgoCD syncs QA.
4. QA automation + smoke checks must pass.
5. Production promotion requires manual approval in GitHub Environments.
6. After approval, workflow updates GitOps values for production using the same resolved digest, commits to `main`, and ArgoCD syncs production.

QA and production deploy the exact same container image digest; only environment configuration differs.

## Workflow Stages

1. **verify-tag-on-main**
   - Ensures the pushed tag commit belongs to `main` ancestry.
2. **promote-qa**
   - Updates `infra/gitops/values-qa.yaml` image repository/tag/digest.
   - Commits `chore(gitops): promote qa to <tag>` to `main`.
   - ArgoCD `autotrading-qa` app auto-sync applies the change.
3. **qa-tests**
   - Waits for QA readiness endpoint to become `UP`.
   - Calls `QA_AUTOMATION_URL` and requires success.
4. **verify-qa-parity**
   - Reads `infra/gitops/values-qa.yaml` from `main` after QA promotion.
   - Verifies QA image repository and digest exactly match the resolved release artifact.
5. **promote-prod**
   - Runs under GitHub `production` environment (manual approval gate).
   - Updates `infra/gitops/values-prod.yaml` image repository/tag/digest using the same digest promoted to QA.
   - Commits `chore(gitops): promote prod to <tag>` to `main`.
   - ArgoCD `autotrading-prod` app auto-sync applies the change.

## Release Tag Helper Workflow

Use [.github/workflows/release-tag.yml](../.github/workflows/release-tag.yml) to create release tags safely:

- Trigger: manual (`workflow_dispatch`)
- Input `tag`: required, must start with `v` (example: `v1.2.3`)
- Input `target_sha`: optional, defaults to latest `origin/main`
- Guardrail: target commit must be reachable from `main`
- Guardrail: tag must not already exist locally or on origin

After the tag is pushed, [.github/workflows/deploy-main.yml](../.github/workflows/deploy-main.yml) runs automatically.

## Local ↔ QA Credential Parity Helper

Use `scripts/sync_credentials_local_qa.py` to provision the same account/agent/API key/broker mapping in both local and QA.

Example:

```bash
python3 scripts/sync_credentials_local_qa.py \
   --account-id acct-qa-demo \
   --account-name "QA Demo Account" \
   --agent-id agent-qa-demo \
   --agent-name "QA Demo Agent" \
   --broker-external-account DU1234567 \
   --qa-database-url "postgresql://user:pass@qa-host:5432/autotrading"
```

Notes:

- If `--api-key` is omitted, a new raw key is generated and printed.
- The same raw key is inserted in both local and QA for parity.
- `--qa-database-url` can be supplied via `QA_DATABASE_URL` env var.

## Required Repository Secrets

- `QA_AUTOMATION_URL` — endpoint that executes QA automation suite.
- `QA_BASE_URL` — base URL used for smoke readiness check.

## Optional Repository/Environment Variables

- `GHCR_IMAGE_REPOSITORY` (default: `ghcr.io/<owner>/<repo>`)

## Required GitHub Environment Configuration

Create environments:

- `qa`
- `production`

For `production`:

- Enable **Required reviewers** to enforce manual approval.
- Optionally add a wait timer and restrict deployment branches to `main`.

For `qa` and `production`:

- Add environment-scoped secrets `QA_AUTOMATION_URL` and `QA_BASE_URL` in `qa`.
- Ensure `production` has required reviewers configured.
- Ensure `GITHUB_TOKEN` has permission to push to `main` (or allow GitHub Actions to bypass branch protection for these chore commits).

## Helm Value Overlays

Environment overlays used by workflow:

- `infra/gitops/values-qa.yaml`
- `infra/gitops/values-prod.yaml`

Both overlays are base templates; the workflow overrides image repository/tag/digest at promotion time.
Digest is the deployment source of truth; release tag is retained as metadata for traceability.

## ArgoCD Manifests

ArgoCD application manifests are included for environment bootstrapping:

- `infra/gitops/argocd/app-qa.yaml`
- `infra/gitops/argocd/app-prod.yaml`

QA app enables automated sync (`prune + selfHeal`).
Production app also enables automated sync; control is enforced by the GitHub `production` approval gate before the production GitOps commit is created.

## Production Image/Pod Model (Recommended)

For your current architecture (8 Java microservices + supporting components), a standard production shape is:

- 1 image per deployable service (for example: ingress, event-processor, agent-runtime, risk, order, ibkr-connector, performance, monitoring-api, and optionally admin-ui).
- 1 release tag per version (for example `v1.2.3`) per image, plus immutable digest.
- Deployments reference image digest (`repository@sha256:...`) so QA and production run byte-identical artifacts.
- One Kubernetes Deployment per service, usually one main container per pod.
- Init work as Kubernetes Jobs/CronJobs (for example Flyway migrations, topic/bootstrap jobs), not baked into app startup containers.

This means you have many images (one per service), many pods (per service replicas), and typically one primary app container per pod.
The important invariant is that QA and production use the same image digests; only vars/secrets/config differ.
