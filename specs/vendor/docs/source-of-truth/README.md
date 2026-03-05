# Source of Truth Specification

This folder is the implementation-grade source of truth for the production broker-integrated trading platform.

## Document Governance
- Status: Active
- Effective date: 2026-03-04
- Primary owners: Trading Core Lead, Broker Connectivity Lead, Policy Platform Lead
- Approval model: cross-team lead signoff + QA/Release gate

## Normative Language
- `MUST`: mandatory for production readiness
- `SHOULD`: strongly recommended; exceptions require documented approval
- `MAY`: optional implementation detail

## Reading Order
1. [01 Product and Technical Requirements](./01-requirements.md)
2. [02 Reference Architecture](./02-reference-architecture.md)
3. [03 Domain Model and Data Contracts](./03-domain-model-data-contracts.md)
4. [04 Service Contract Catalog](./04-service-contract-catalog.md)
5. [05 Operations, Security, and Reliability](./05-operations-security-reliability.md)
6. [06 Team Implementation Plan](./06-team-implementation-plan.md)
7. [07 Traceability and Acceptance Matrix](./07-traceability-acceptance-matrix.md)
8. [08 Change Control and Versioning](./08-change-control-versioning.md)

## Canonical Constraints
1. Unknown order state MUST freeze new opening orders.
2. Missing broker status at 60 seconds MUST transition to `UNKNOWN_PENDING_RECON`.
3. Idempotency key MUST be present for ingress submissions, signal creation, and order intent creation.
4. Resume from frozen mode MUST require clean reconciliation and operator acknowledgement.
5. Control actions MUST be actor-authenticated and audit-recorded.
6. OPA/policy failure for opening orders MUST fail closed.
7. Production policy activation MUST require signed bundle verification and approval.
8. Policy denials MUST include explainability metadata (`policy_version`, rule/reason fields).

## Best-Practice Baseline
- [Trading Best Practices Baseline](../TRADING_BEST_PRACTICES_BASELINE.md)
