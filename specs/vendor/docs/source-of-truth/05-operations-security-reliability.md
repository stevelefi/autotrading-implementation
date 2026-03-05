# 05 Operations, Security, and Reliability

## Reliability Model
- Consistency-first operation model.
- Unknown execution state triggers safety freeze.
- Reconciliation required before unfreeze.
- Safety controls override availability goals during uncertainty.

## Failure Handling Matrix
| Incident | Immediate action | Recovery |
|---|---|---|
| Ingress auth/schema failure spike | reject and audit each request | fix producer config, verify ingress diagnostics |
| No broker status in 60s | mark unknown + freeze | reconciliation + ack |
| Broker disconnect | freeze opening orders | reconnect and reconcile |
| OPA unavailable | fail-closed on opening orders | restore sidecar/bundle |
| OPA timeout trend | fail-closed rejects + alert | tune timeout budget, investigate sidecar saturation |
| OPA schema mismatch | fail-closed reject with `OPA_SCHEMA_ERROR` | rollback bundle or contract change |
| Bundle activation/signature failure | block activation and alert | re-publish signed bundle, re-run promotion checks |
| Kafka outage | continue via outbox backlog | replay after recovery |
| DB failure | pause writes and protect state | recover DB, replay outbox |

## Security Controls
- Secrets managed outside repo.
- Role-based access for control APIs.
- Actor identity required for mutating operations.
- Audit logs retained per policy.
- Control actions fail closed if auth/audit path is unavailable.
- Policy bundles must be signed and verified before activation.
- Bundle source endpoints must be allowlisted.

## Observability Minimum
- P0 alerts for unknown state and timeout breaches.
- Dashboards for latency, lag, position drift, and reconciliation state.
- Trace propagation across services.
- Policy path telemetry includes OPA latency/error rate, bundle freshness age, and activation failures.

## Runbook Requirements
- timeout and freeze incident runbook
- reconnect and reconciliation runbook
- policy rollback runbook
- policy bundle activation failure runbook
- release rollback runbook

## Normative Operational Requirements
1. `kill-switch` and trading mode changes MUST be actor-audited.
2. Connector single-writer guarantee MUST be continuously monitored.
3. Alert-to-runbook mapping MUST exist for every P0/P1 alert.
4. Reconciliation evidence MUST include mismatch details and ack actor.
5. Production promotion MUST be blocked by unresolved P0 consistency findings.
6. Production policy activation MUST be blocked without signed artifact, approval, and tested rollback target.

## Related References
- [Trading Best Practices Baseline](../TRADING_BEST_PRACTICES_BASELINE.md)
- [Observability and Alerting](../OBSERVABILITY_AND_ALERTING.md)
- [Security and Compliance](../SECURITY_AND_COMPLIANCE.md)
- [Policy Bundle Contract](../contracts/policy-bundle-contract.md)
- [Policy Decision Audit Contract](../contracts/policy-decision-audit-contract.md)
