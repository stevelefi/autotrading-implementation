# 01 Product and Technical Requirements

## Business Goal
Operate a controlled automated trading platform that can execute and monitor multi-agent trading with strong consistency and full auditability.

## Trading Scope (Current Release)
- Equities: DB whitelist only
- Futures: MNQ only
- Broker: IBKR via TWS API and IB Gateway
- Modes: Paper and Production

## Functional Requirements
1. Multi-agent signal ingestion and independent attribution.
2. Policy-evaluated pre-trade approval/rejection.
3. Deterministic order lifecycle tracking.
4. Real-time monitoring and historical analysis.
5. Operational controls: kill switch, freeze mode, reconciliation run, resume ack.
6. Trader UI event ingestion through authenticated ingress API/WebSocket workflows.
7. External event ingestion through authenticated ingress WebHook/API/gRPC endpoints.
8. Raw intake payloads must be stored immutably for troubleshooting and replay.
9. Raw events must be transformed to canonical trade events and routed to agent processing topics.
10. Pre-trade policy decisions must produce explainability fields (`policy_version`, `rule_set`, rule/reason metadata) for operator visibility and audit.

## Non-Functional Requirements
### Consistency
- Order submission path MUST be idempotent.
- Execution accounting MUST be deduplicated by `exec_id`.

### Reliability
- The system SHOULD degrade to safety (freeze) when broker state is uncertain.
- Recovery MUST include deterministic reconciliation.
- Connector writer lease MUST prevent split-brain broker submission paths.

### Latency SLOs
- signal -> risk decision (`gRPC`) p95 &lt;= 40 ms
- risk decision -> intent created (`gRPC`) p95 &lt;= 30 ms
- order intent -> broker submit ack (`gRPC`) p95 &lt;= 50 ms
- fill -> position update p95 &lt;= 1 second
- monitoring SSE freshness p95 &lt;= 2 seconds

### Security and Audit
- Mutating control actions MUST include actor identity.
- Critical state changes MUST be audit logged.
- Production policy bundle activation MUST require approval and signed artifact verification.

### Time and Session Discipline
- All persisted timestamps MUST be UTC.
- Clock skew across services SHOULD stay within bounded drift (target <= 250 ms).
- Order creation MUST respect policy-defined trading session windows.

## Explicit Invariants
1. `idempotency_key` uniquely identifies request intent.
2. One `order_intent_id` maps to at most one broker order submission path.
3. `UNKNOWN_PENDING_RECON` implies `trading_mode = FROZEN`.
4. `FROZEN -> NORMAL` transition requires reconciliation clean state + operator ack.
5. Control mutations require authenticated actor and persisted audit event.
6. Trade events from non-agent sources must persist source attribution and dedupe identity.
7. Canonical lineage must be preserved: `raw_event_id -> trade_event_id -> signal_id -> order_intent_id`.
