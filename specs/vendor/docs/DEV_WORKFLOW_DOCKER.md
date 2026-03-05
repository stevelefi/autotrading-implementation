# Docs-First DEV Workflow (Planning + Site Generation)

This repository is scoped to planning/design/architecture documentation and site generation.
It does not host runnable microservices, database stacks, or broker simulators.

## Why Docs-First
- Faster onboarding for documentation contributors.
- No local runtime stack required for repo validation.
- Reproducible checks with a small Python toolchain.

## Local Commands

## Setup docs environment
```bash
python3 -m venv .venv-docs
source .venv-docs/bin/activate
pip install -r requirements-docs.txt
```

## Run docs preview
```bash
mkdocs serve
```
Open: `http://127.0.0.1:8000/autotrading/`

## Run local docs checks
```bash
./scripts/validate-api-contracts.sh
mkdocs build --strict
```

## Run plan-sync validation
```bash
mvn -q -pl tools/plan-sync -am package
java -jar tools/plan-sync/target/plan-sync.jar validate --tasks docs/tasks.yaml
```

## DEV Policy
1. Edit docs/contracts locally.
2. Run API contract + MkDocs checks before PR.
3. Open PR only after checks pass.
4. Keep runtime implementation artifacts in their dedicated runtime repositories.
