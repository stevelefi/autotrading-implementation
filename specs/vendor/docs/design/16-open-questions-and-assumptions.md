# 16 Open Questions and Assumptions

## Assumptions
- Consistency has priority over availability during uncertainty.
- Initial deployment remains single VM with controlled risk.
- OPA remains policy source of truth.

## Open Questions
- Live-trading rollout exposure ladder and capital allocation policy.
- Exact retention durations by compliance requirement.
- Future multi-broker strategy and abstractions.

## 2026 Planning Tracking
| Question | Owner Team | Target Decision Date |
|---|---|---|
| Live-trading exposure ladder and capital allocation policy | Trading Core + Risk/Compliance | March 17, 2026 |
| Compliance retention durations by data class | Data Platform + Compliance | March 24, 2026 |
| Multi-broker abstraction and migration triggers | Broker Connectivity + Trading Core | April 7, 2026 |
| Reconciliation orchestration authority | Trading Core + API/UI | March 17, 2026 |
| Operator manual override/audit rule boundaries | API/UI + SRE + Compliance | March 24, 2026 |

## Decision Log Policy
- Every resolved question should become an ADR or an update to an existing ADR.
