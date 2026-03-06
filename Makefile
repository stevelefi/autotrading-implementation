SHELL := /bin/bash

COMPOSE := docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml

.PHONY: up down down-infra logs build restart validate ci-local test-unit test-e2e test-coverage-core smoke-local rollback-local verify-spec helm-lint helm-template pre-commit

APP_SERVICES := ingress-gateway-service event-processor-service agent-runtime-service risk-service order-service ibkr-connector-service performance-service monitoring-api

build:
	$(COMPOSE) build

restart: down build up

up:
	$(COMPOSE) up -d --remove-orphans

validate:
	@echo "=== Container status ==="
	$(COMPOSE) ps
	@echo ""
	@echo "=== Running smoke suite ==="
	$(MAKE) smoke-local

ci-local:
	@STATUS=0; \
	$(MAKE) build up || STATUS=$$?; \
	if [ $$STATUS -eq 0 ]; then $(MAKE) validate || STATUS=$$?; fi; \
	if [ $$STATUS -ne 0 ]; then \
		echo ""; \
		echo "=== ci-local FAILED (exit $$STATUS) — last 40 log lines per service ==="; \
		$(COMPOSE) logs --tail=40 2>&1; \
	fi; \
	$(MAKE) down; \
	exit $$STATUS

down:
	$(COMPOSE) stop $(APP_SERVICES)
	$(COMPOSE) rm -f $(APP_SERVICES)

down-infra:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f --tail=200

test-unit:
	mvn -B -DskipITs=true test

test-e2e:
	mvn -B -pl tests/e2e -am test

test-coverage-core:
	mvn -B -Pcoverage-core -pl libs/reliability-core,services/ingress-gateway-service,services/risk-service,services/order-service,services/ibkr-connector-service -am verify

smoke-local:
	python3 scripts/smoke_local.py

rollback-local:
	@echo "Rollback primitive: stopping local services and preserving DB volume snapshot strategy"
	$(COMPOSE) stop

verify-spec:
	python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json

helm-lint:
	helm lint infra/helm/charts/trading-service

helm-template:
	helm template trading-service infra/helm/charts/trading-service -f infra/helm/charts/trading-service/values.yaml

pre-commit: test-unit test-coverage-core verify-spec helm-lint helm-template
	@echo "All local checks passed."

