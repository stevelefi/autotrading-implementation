# 09 Security Threat Model

## Threat Categories
- Credential leakage
- Unauthorized order controls
- Message tampering
- Replay/duplicate command attacks
- Dependency compromise

## Mitigations
- Secret management with rotation.
- AuthN/AuthZ for control endpoints.
- TLS in transit, signed artifacts for policy bundles.
- Idempotency keys and dedupe protections.
- Immutable audit logs for sensitive actions.

## High-Risk Controls
- Kill switch must require authenticated actor.
- Trading mode changes must be auditable.
- Reconciliation resume must be double-checked.
