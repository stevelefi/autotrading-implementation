# Policy Bundle Contract

## Owner Team
Policy Platform

## Purpose
Define immutable, signed, and versioned policy bundle artifacts used by OPA sidecars in `risk-service` pods.

## Artifact Boundaries
- Logic bundle: Rego policies and static decision logic.
- Data bundle: controlled dynamic limits and configuration values.
- Both bundles are independently versioned and signed.

## Required Metadata
Each published bundle must include:
- `policy_version`
- `bundle_checksum_sha256`
- `bundle_signer`
- `compatibility_version`
- `published_at_utc`
- `activation_state` (`staged`, `active`, `rollback`)

## Activation States
- `staged`: candidate bundle present but not active.
- `active`: current production evaluation bundle.
- `rollback`: previously active bundle reactivated due to incident or failed promotion.

## Promotion Flow
1. Author change in dedicated policy repository.
2. CI gates pass (lint, tests, regression vectors, latency checks).
3. Signed bundle is published with immutable version.
4. Promote `dev` -> `paper` -> `prod`.
5. Production promotion requires explicit approval.
6. Rollback target must be verified before production activation.

## CI Gates (Mandatory)
1. Rego lint/static checks.
2. Unit tests for decision rules.
3. Regression vector suite from historical paper-trading decisions.
4. Performance budget checks for policy evaluation latency.
5. Schema compatibility check for `opa.policy.input.v1` and `opa.policy.decision.v1`.

## Runtime Loading Contract
1. OPA sidecar loads bundle from allowlisted registry/source only.
2. Signature verification must succeed before activation.
3. Failed load must keep last-known-good active bundle.
4. Failed load triggers reason code `BUNDLE_LOAD_ERROR` and alert.
5. `risk-service` readiness remains failed until an active valid bundle is available.

## Audit Contract
Each activation/rollback record must include:
- `trace_id`
- `actor_id`
- `environment`
- `policy_version`
- `action` (`ACTIVATE`, `ROLLBACK`)
- `reason`
- `timestamp_utc`
- `result`

## Failure Behavior
1. Signature verification failure: reject activation and alert.
2. Schema incompatibility: reject activation and alert.
3. Source unavailable:
   - keep active bundle,
   - continue evaluation,
   - alert on freshness threshold breach.

## Related Contracts
- [Rule Engine (OPA)](../RULE_ENGINE_OPA.md)
- [Risk Service Contract](./risk-service.md)
- [Policy Decision Audit Contract](./policy-decision-audit-contract.md)
