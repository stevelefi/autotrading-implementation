# PR Readiness Checklist

Use this prompt before every `git push` and before creating a GitHub PR.

---

## Pre-Push Gate (must be 100% green)

```bash
python3 scripts/check.py
```

This runs 7 checks in sequence and prints a pass/fail summary. A single ❌ blocks the PR.

### Individual checks (if you need to run them separately)

```bash
# 1. Branch name validation
python3 scripts/branch_check.py
# Must print OK. Branch must match: feature/, bugfix/, hotfix/, chore/, release/, or AT-NNNN-

# 2. Spec sync verification
python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
# Must print "Spec sync verified" with no drift

# 3. Unit tests — zero failures tolerated
python3 scripts/test.py unit
# Expect: BUILD SUCCESS, all tests green

# 4. JaCoCo coverage gate (≥ 50% line coverage on 5 core modules)
python3 scripts/test.py coverage
# Expect: all 5 modules above threshold

# 5. E2E tests — all 5 test classes must pass
python3 scripts/test.py e2e
# Classes: VerticalSliceGrpcFlowTest, MandatoryScenariosTest, ReliabilityDrillTest,
#          DataConsistencyTest, FlywayMigrationTest

# 6. Helm lint + template render
helm lint infra/helm/charts/trading-service
helm template trading-service infra/helm/charts/trading-service \
  -f infra/helm/charts/trading-service/values.yaml > /dev/null

# 7. Smoke suite (requires live stack)
python3 scripts/stack.py up
python3 scripts/smoke_local.py   # must exit 0 — 6 phases
python3 scripts/stack.py down
# Write smoke report path to PR body
```

### Fast iteration (skip e2e)
```bash
python3 scripts/check.py --fast
```

---

## PR Body Template

```markdown
## Summary
<!-- What does this change do? One paragraph. -->

## Spec Reference
Pinned spec: `spec-v1.0.1-m0m1` (from `SPEC_VERSION.json`)
<!-- Link to the specific contract/doc in specs/vendor/docs/ if applicable -->

## Changes
- `services/<service>/…` — 
- `db/migrations/V<n>__…sql` — (if applicable)
- `libs/<lib>/…` — 

## Test Evidence
- Unit tests: `python3 scripts/test.py unit` — all green
- Coverage: above 50% on all 5 core modules
- E2E: all 5 test classes green
- Smoke report: `reports/blitz/e2e-results/smoke-local-<timestamp>.md`

## Migration Evidence (if applicable)
```
docker logs flyway-init-1 2>&1 | tail -5
Successfully applied N migrations to schema "public"
```

## Checklist
- [ ] `python3 scripts/branch_check.py` prints OK
- [ ] `python3 tools/spec_sync.py verify …` passes
- [ ] `python3 scripts/test.py unit` — 0 failures
- [ ] `python3 scripts/test.py coverage` — all modules ≥ 50%
- [ ] `python3 scripts/test.py e2e` — all 5 classes green
- [ ] Helm lint + template render pass
- [ ] `python3 scripts/smoke_local.py` exits 0
- [ ] No secrets staged (`git diff --staged | grep -i password`)
- [ ] No `target/` directories staged
- [ ] No hard-coded image tags in `values.yaml`
- [ ] Smoke report path included above
```

---

## Commit Message Format

```
<type>(<scope>): <imperative summary under 72 chars>

<optional body — explain WHY, not WHAT>

Refs: #<issue-number>
```

Types: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `ci`, `perf`

Examples:
```
feat(ingress): add Bearer API-key authentication gate
fix(risk): correct Persistable isNew flag on RiskDecision entity
chore(db): add V10 account model migration
docs(readme): update smoke suite to 6 phases
```

Use `python3 scripts/pr.py` to avoid shell-quoting issues:
```bash
python3 scripts/pr.py \
  --branch feature/my-feature \
  --title "feat(service): my change" \
  --body "See checklist in PR template"
```

---

## Large / Cross-Cutting Changes — Additional Gate

For: new services, DB migrations, Helm / infra / config changes, gRPC or HTTP contract mods,
multi-module rewrites.

```bash
python3 scripts/stack.py ci
# Runs: down → build → up → validate → teardown
# Use when the build cache may be stale
```

---

## Blitz Contract Freeze

Runtime API / Kafka topic / proto / schema changes are **frozen during blitz**.

- A blocking defect requiring a contract change must be raised in the spec repo **first**.
- No contract change is allowed until: new tagged baseline + spec bump PR + `spec_sync.py sync`.
- Then open a dedicated "spec bump" PR in this repo.
