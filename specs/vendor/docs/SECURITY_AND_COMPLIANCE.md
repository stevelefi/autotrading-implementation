# Security and Compliance

## Identity and Access Control
1. Use least-privilege service identities for Postgres, Kafka, and control APIs.
2. Separate credentials and roles by environment (`local`, `paper`, `prod`).
3. Require authenticated actor identity for all mutating control actions.
4. Restrict dangerous controls (`kill-switch`, `resume`) to explicit operator roles.
5. Enforce approval workflow for production policy bundle activation.
6. Require immutable signer identity on policy bundle metadata.

## Control Plane Protection
1. Route all operator controls through `monitoring-api`; no direct UI-to-DB or UI-to-Kafka writes.
2. Use request identity (`trace_id`, `request_id`) and actor identity for every control mutation.
3. Apply rate limits and replay protection on control endpoints.
4. Fail closed when authorization or audit write is unavailable.

## Secrets and Key Management
1. Store CI secrets in GitHub encrypted secrets.
2. Inject runtime secrets from deployment environment, not from repo.
3. Rotate broker/API/database secrets on fixed schedule and after incidents.
4. Do not expose account identifiers or auth material in logs.
5. Maintain break-glass secret rotation runbook.

## Network and Runtime Hardening
1. Apply network allowlists for broker access and inter-service traffic.
2. Deny by default for non-required egress paths.
3. Keep container images minimal and patched.
4. Enforce TLS for external traffic and authenticated internal access where applicable.
5. Monitor for connector split-brain and unauthorized control attempts.

## Auditability and Compliance Evidence
Record and retain immutable evidence for:
1. kill switch toggles,
2. trading mode changes,
3. reconciliation start/result/ack,
4. policy bundle activation/rollback,
5. policy signature verification results,
6. release approvals and rollback decisions.

Mandatory audit fields:
- `trace_id`
- `actor_id`
- `action`
- `target`
- `result`
- `timestamp_utc`

## Data Handling and Retention
1. Minimize sensitive data in logs and events.
2. Redact credentials and account identifiers before persistence.
3. Define retention windows by data class (operational logs, events, audit records).
4. Keep archive/restore procedure tested and documented.

## Security Review Checklist
1. Are all mutating endpoints authenticated and authorized?
2. Is every control action auditable with actor + trace + UTC timestamp?
3. Are secret sources externalized and rotation-ready?
4. Is network exposure minimized and documented?
5. Are runbooks available for security incident response?

## Related References
- [API Specification](./API_SPEC.md)
- [Trading Best Practices Baseline](./TRADING_BEST_PRACTICES_BASELINE.md)
- [Source of Truth: Ops, Security, Reliability](./source-of-truth/05-operations-security-reliability.md)
