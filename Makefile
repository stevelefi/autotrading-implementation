SHELL := /bin/bash

COMPOSE := docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml

.PHONY: up down logs build restart validate test-unit test-e2e test-coverage-core smoke-local rollback-local verify-spec helm-lint helm-template pre-commit

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

down:
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

