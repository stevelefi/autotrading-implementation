# 07 Traceability and Acceptance Matrix

## Matrix Purpose
Guarantee every requirement maps to implementation, test evidence, and operations control.

| Requirement | Owner | Component | Test Evidence | Alert/Runbook |
|---|---|---|---|---|
| Immutable ingress raw persistence before publish | API/UI | ingress-gateway-service | ingress acceptance and idempotency tests | ingress error diagnostic runbook |
| No duplicate order execution | Trading Core + Broker | order-service + connector | duplicate key/callback tests | timeout/runbook + duplicate alert |
| Freeze on unknown state | Trading Core | order-service | 60s timeout E2E test | P0 alert + reconciliation runbook |
| Dynamic runtime policy updates | Policy Platform | OPA sidecar + bundle | policy rollout test | policy rollback runbook |
| Signed policy activation only | Policy Platform + SRE | policy bundle pipeline | signature verification tests | bundle activation failure runbook |
| Policy explainability on rejects | Policy Platform + API/UI | risk-service + monitoring-api | reject payload/SSE validation tests | policy dashboard + audit runbook |
| Reconciliation gate for resume | Broker + API | connector + monitoring-api | reconciliation game-day | recon runbook |
| Full operator visibility | API/UI + SRE | monitoring + dashboard | UAT screenshots and logs | dashboard + alert coverage |

## Acceptance Gates
1. Contract tests all green.
2. Consistency scenario tests all green.
3. Incident drills completed.
4. Soak test exit criteria met.
