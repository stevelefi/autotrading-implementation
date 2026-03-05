# 06 Team Implementation Plan

## Planning Structure
Work is split by execution streams with clear owners and exit criteria.

## Stream A: Trading Core
### Tasks
- Implement event-processor routing from ingress normalized events.
- Implement gRPC command flow (`EvaluateSignal` -> `CreateOrderIntent` -> `SubmitOrder`).
- Implement state machine transitions and legal guards.
- Implement 60s deadline watchdog.
- Integrate freeze-mode gates.

### Exit Criteria
- Timeout scenario tests pass.
- Duplicate idempotency tests pass.

## Stream B: Broker Connectivity
### Tasks
- Implement single active lease/fencing.
- Normalize callback mapping.
- Expose gRPC broker command services (submit/cancel/replace).
- Integrate reconnect/reconciliation hooks.

### Exit Criteria
- No duplicate broker submit under retry drills.

## Stream C: Policy Platform
### Tasks
- Build OPA bundle pipeline.
- Author initial production rule packs.
- Add schema compatibility checks (`opa.policy.input.v1`, `opa.policy.decision.v1`).
- Add bundle signature verification and immutable version metadata.
- Add production approval gate and rollback target verification.
- Emit policy decision audit events with explainability fields.
- Implement rollback playbook.

### Exit Criteria
- Fail-closed behavior verified.
- policy rollout and rollback tested.
- unsigned/invalid bundle activation is blocked.
- policy decision audit coverage is complete for all evaluated signals.

## Stream D: Data Platform
### Tasks
- Finalize schema migrations.
- Implement outbox/inbox framework.
- Add replay and reconciliation support queries.

### Exit Criteria
- Restore/replay drill validated.

## Stream E: API/UI
### Tasks
- Implement ingress protocols (WebHook/API/gRPC/WebSocket) and operational APIs.
- Ensure legacy monitoring-api intake endpoints forward to ingress.
- Build dashboard views for frozen/unknown/recon states.
- Integrate runtime config consumers for Kubernetes `ConfigMap` and `Secret`.

### Exit Criteria
- Operator UAT signoff.

## Stream F: SRE + QA/Release
### Tasks
- Alerting and dashboard setup.
- Deploy Kubernetes-native discovery/config controls (Service DNS, probes, RBAC, config rollout guards).
- Chaos and game-day execution.
- Soak testing and release gate management.

### Exit Criteria
- 10-day paper soak with zero unexplained drift.
