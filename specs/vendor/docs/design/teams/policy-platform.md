# Policy Platform Team Guide

## Scope
Owns OPA policy lifecycle, governance controls, and risk-policy explainability contracts.

## Owned Components/Repos
- Components: OPA sidecar policy path, policy bundle pipeline, decision audit schema
- Repos: `autotrading-policy`, `autotrading` (contracts and source-of-truth docs)

## Core Responsibilities
- Author and test policy bundles with versioned promotion controls.
- Enforce signature validation and approved bundle activation flow.
- Maintain input/output schema compatibility with Trading Core.
- Maintain deny reason taxonomy and decision explainability fields.

## Non-Negotiables
- OPA decision path remains fail-closed for opening-order checks.
- Unsigned or invalid bundles cannot be promoted.
- Every policy change includes corresponding regression vectors.

## Handoffs
- Inbound from Trading Core: risk input contract and runtime decision context.
- Inbound from QA/Release: gate and certification evidence requirements.
- Outbound to Trading Core and API/UI: policy decisions, reason codes, policy version metadata.

## Acceptance Signals
- Bundle promotion and rollback drills pass in paper environment.
- Schema contract checks pass for `opa.policy.input.v1` and `opa.policy.decision.v1`.
- Decision audit events include policy version and deterministic deny reasons.

