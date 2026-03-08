# Contributing

This repository follows [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow).

## Branch Workflow

```
main
  └── <type>/<short-description>   ← branch here
          └── (commits)
                  └── PR → main
```

### 1. Start from an up-to-date `main`

```bash
git checkout main
git pull
```

### 2. Create a feature branch

Branch names must follow `<type>/<short-description>`:

| Type | Use for |
|---|---|
| `feat` | New feature or behaviour |
| `fix` | Bug fix |
| `chore` | Build, deps, tooling changes |
| `refactor` | Code restructure, no behaviour change |
| `docs` | Documentation only |
| `test` | Tests only |
| `ci` | CI/CD or workflow changes |
| `hotfix` | Urgent prod fix |
| `release` | Release preparation |

```bash
git checkout -b feat/order-idempotency-key
```

### 3. Make changes, commit often

Keep commits focused. Use [Conventional Commits](https://www.conventionalcommits.org/) format:

```
feat(order): add idempotency key to OrderRequest
fix(risk): correct VaR overflow when position is zero
chore(deps): bump spring-boot to 3.3.5
```

### 4. Push and open a Pull Request

```bash
git push -u origin feat/order-idempotency-key
gh pr create --base main --title "feat(order): add idempotency key"
```

- Keep PRs small and focused on a single concern.
- Link the relevant issue in the PR description.
- Include the pinned spec ref from `SPEC_VERSION.json` if the change touches contracts.

### 5. Code review

- At least one reviewer approval before merging.
- All review comments resolved before merge.

### 6. Merge and delete the branch

Use **squash merge** to keep `main` history clean:

```bash
gh pr merge --squash --delete-branch
```

`main` is always in a releasable state — never push broken code to `main`.

---

## Local Checks (before pushing)

```bash
# Build + tests
mvn -B test

# Coverage gate (core modules ≥ 50% line coverage)
make test-coverage-core

# Spec pin verify
python tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json

# Helm validate (if changing infra/helm)
helm lint infra/helm/charts/trading-service
helm template trading-service infra/helm/charts/trading-service \
  -f infra/helm/charts/trading-service/values.yaml > /dev/null

# Full stack smoke gate (requires Docker — mandatory before opening a PR)
make up          # bring up postgres + redpanda + ibkr-sim + all 8 services
make smoke-local # validate all 5 signal-path phases
make down        # tear down after pass
```

Run unit + coverage + spec + Helm checks with:

```bash
make pre-commit
```

Run everything together (mirrors CI):

```bash
make ci-local
```
