SHELL := /bin/bash

.PHONY: up down logs test-unit test-e2e test-coverage-core smoke-local rollback-local verify-spec helm-lint helm-template

up:
	docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml up -d --remove-orphans

down:
	docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml down -v

logs:
	docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml logs -f --tail=200

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
	docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml stop

verify-spec:
	python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json

helm-lint:
	helm lint infra/helm/charts/trading-service

helm-template:
	helm template trading-service infra/helm/charts/trading-service -f infra/helm/charts/trading-service/values.yaml
