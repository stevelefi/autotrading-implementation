# Policy Platform Team Guide

## Scope
Owns OPA policy packages, bundle publishing, policy governance, and runtime policy observability.

## Owned Components
- OPA bundle repo and CI
- policy validation tests
- policy deployment and rollback
- bundle signing and signature verification controls
- policy decision audit contract stewardship

## Policy Areas
- Instrument whitelist
- Time window controls
- Quantity and exposure limits
- Daily loss and trade frequency controls
- Kill switch and trading mode checks

## Governance Rules
- Every policy change must include test case updates.
- Every bundle gets immutable version tag.
- Rollback path must be pre-verified.
- Production activation requires explicit approval.
- Production activation requires valid signature verification.

## Failure Behavior
- OPA unavailable => fail-closed (reject openings).
- Bundle load failure => last-known-good bundle and alert.
- Schema mismatch (`opa.policy.input.v1` or `opa.policy.decision.v1`) => fail-closed deny and alert.

## Team Outputs
- Stable policy decision contract for risk-service.
- Decision logs with policy version markers.
- Reason-code taxonomy aligned across risk-service, monitoring, and audit streams.
