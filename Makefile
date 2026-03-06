SHELL := /bin/bash
STACK := python3 scripts/stack.py

.PHONY: up up-infra up-app down down-infra restart restart-app build status logs validate ci-local \
        test-unit test-e2e test-coverage-core smoke-local rollback-local verify-spec \
	helm-lint helm-template pre-commit \
	docs-install docs-serve docs-build docs-up docs-down docs-validate

up:
	$(STACK) up

up-infra:
	$(STACK) infra-up

up-app:
	$(STACK) app-up

down:
	$(STACK) app-down

down-infra:
	$(STACK) down

restart: down-infra build up

restart-app:
	$(STACK) restart-app

status:
	$(STACK) status

logs:
	$(STACK) logs

validate:
	$(STACK) validate

ci-local:
	$(STACK) ci

test-unit:
	mvn -B -DskipITs=true test

test-e2e:
	mvn -B -pl tests/e2e -am test

test-coverage-core:
	mvn -B -Pcoverage-core -pl libs/reliability-core,services/ingress-gateway-service,services/risk-service,services/order-service,services/ibkr-connector-service -am verify

smoke-local:
	python3 scripts/smoke_local.py

rollback-local:
	@echo "Rollback primitive: stopping app services (infra volumes preserved)"
	$(STACK) app-down

verify-spec:
	python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json

helm-lint:
	helm lint infra/helm/charts/trading-service

helm-template:
	helm template trading-service infra/helm/charts/trading-service -f infra/helm/charts/trading-service/values.yaml

pre-commit: test-unit test-coverage-core verify-spec helm-lint helm-template
	@echo "All local checks passed."

# ── Documentation (MkDocs) ───────────────────────────────────────────────────

docs-install:
	pip install -r requirements-docs.txt

docs-serve:
	mkdocs serve

docs-build:
	mkdocs build --strict --site-dir _site
	@echo "Site built → _site/"

docs-up:
	@echo "Starting docs preview containers..."
	docker compose -f docker-compose.docs.yml up -d --pull missing
	@echo ""
	@echo "  impl docs  →  http://localhost:8001"
	@echo "  spec docs  →  http://localhost:8002"
	@echo ""
	@echo "Run 'make docs-down' to stop."

docs-down:
	docker compose -f docker-compose.docs.yml down

docs-validate:
	@echo "=== Validating impl docs (strict) ==="
	docker compose -f docker-compose.docs.yml run --rm impl-docs build --strict --site-dir /tmp/_site
	@echo "=== Validating spec docs (strict) ==="
	docker compose -f docker-compose.docs.yml run --rm spec-docs build --strict --site-dir /tmp/_site
	@echo "=== Both sites validated successfully ==="

