SHELL := /bin/bash

.PHONY: up down logs test-unit test-e2e smoke-local rollback-local verify-spec

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

smoke-local:
	python3 scripts/smoke_local.py

rollback-local:
	@echo "Rollback primitive: stopping local services and preserving DB volume snapshot strategy"
	docker compose --env-file infra/local/.env.compose.example -f infra/local/docker-compose.yml stop

verify-spec:
	python3 tools/spec_sync.py verify --dest specs/vendor --version-file SPEC_VERSION.json
