# API Contract Validation

This document defines how we keep API design consistent across implementation teams.

## Contract Sources of Truth
1. Human-readable API behavior: `docs/API_SPEC.md`.
2. Service-level API role and SLOs: `docs/contracts/monitoring-api.md`.
3. Machine-readable contract: `docs/contracts/monitoring-api.openapi.yaml`.
4. Ingress protocol contracts: `docs/contracts/ingress-gateway-service.md`, `docs/contracts/ingress-gateway-service.proto`, and ingress JSON schemas.

These contract artifacts must stay aligned for every endpoint/protocol change.

## Validation Command
```bash
./scripts/validate-api-contracts.sh
```

What this command checks:
1. OpenAPI syntax is valid.
2. Endpoint inventory in `docs/API_SPEC.md` equals endpoint inventory in OpenAPI.

## Required Manual Checks (Current Iteration)
1. Ingress contract docs/proto/schema references are still valid and version-aligned.
2. Topic contract updates in `docs/KAFKA_EVENT_CONTRACTS.md` are reflected in schema artifact backlog.
3. Compatibility/deprecation notes are present when endpoint ownership changes.

Reference:
- `docs/CONTRACTS_READINESS_AND_EXTENSIBILITY.md`

## CI Gate Recommendation
Add this check to PR validation for any docs or API-contract changes:
```bash
./scripts/validate-api-contracts.sh
mkdocs build --strict
```

## Definition of "API Works" (Design Phase)
During design and planning, API readiness means:
1. Endpoints are contract-complete and validated.
2. Request/response/error behavior is explicit and stable.
3. Auth and idempotency behavior is documented.
4. Integration test scenarios are defined and traceable to tasks.

## Ownership
- Primary owner: API/UI Team.
- Required reviewers: Trading Core, SRE.
- Audit reviewer for protected controls: Compliance.
