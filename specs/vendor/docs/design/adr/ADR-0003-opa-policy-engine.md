# ADR-0003: OPA as Policy Engine

## Status
Accepted

## Decision
Use OPA/Rego for runtime policy decisions with fail-closed behavior.

## Consequences
- Runtime policy updates without service redeploy.
- Strong policy versioning and auditability required.
