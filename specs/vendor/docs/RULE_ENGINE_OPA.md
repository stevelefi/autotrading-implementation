# Rule Engine (OPA)

## Why OPA
- Centralized policy-as-code.
- Runtime updates without redeploying trading services.
- Explicit versioning and rollback.
- Deterministic fail-closed behavior for pre-trade risk decisions.

## Runtime Model
- OPA runs as sidecar to each `risk-service` pod.
- `risk-service` calls local OPA endpoint over loopback/local pod network only.
- OPA is authoritative for opening-order allow/deny.
- Fail-closed policy: if OPA is unavailable, timed out, or returns schema-invalid output, reject opening orders.

## Non-Goals (This Iteration)
- No Drools adoption.
- No Python hot-path policy replacement.
- No remote/serverless policy evaluation in pre-trade decision path.

## Policy Contract Versioning
- Input contract: `opa.policy.input.v1`.
- Output contract: `opa.policy.decision.v1`.
- Any breaking schema change requires `v2` and compatibility window.
- `risk-service` rejects schema-mismatched OPA outputs with reason code `OPA_SCHEMA_ERROR`.

## Policy Input (example)
```json
{
  "schema_version": "opa.policy.input.v1",
  "trace_id": "trc-7f2ad8f5",
  "signal_id": "sig-20260303-000123",
  "agent_id": "agent-1",
  "instrument_id": "eq_tqqq",
  "asset_type": "EQUITY",
  "symbol": "TQQQ",
  "side": "BUY",
  "qty": 10,
  "now_utc": "2026-03-03T16:10:00Z",
  "daily_realized_pnl": -120.5,
  "daily_unrealized_pnl": -25.0,
  "trades_today": 12,
  "current_position": 30,
  "kill_switch": false,
  "trading_mode": "NORMAL"
}
```

## Policy Output (example)
```json
{
  "schema_version": "opa.policy.decision.v1",
  "allow": true,
  "deny_reasons": [],
  "matched_rule_ids": ["SESSION_WINDOW", "MAX_NET_POSITION"],
  "failure_mode": "NONE",
  "applied_policy_version": "2026.03.03-1",
  "applied_rule_set": "default-prod"
}
```

## Rule Categories
- instrument whitelist
- market session windows
- order quantity range
- net position cap
- daily loss cap
- max trades/day
- cooldown seconds
- global kill switch and trading mode

## Policy Logic vs Policy Data
- Hard invariants and decision logic stay in Rego bundles.
- Dynamic limits/config stay in controlled policy data bundle.
- Data-only changes must still be versioned and signed.
- `risk-service` logs both logic and data bundle versions in decision records.

## Bundle Metadata Contract
Required metadata:
- `policy_version`
- `bundle_checksum_sha256`
- `bundle_signer`
- `compatibility_version`
- `published_at_utc`
- `activation_state` (`staged`, `active`, `rollback`)

## Governance and Promotion Pipeline (Required)
1. Policy changes are authored in a dedicated policy repository.
2. CI gates run on every change:
   - Rego lint and static checks.
   - Unit tests.
   - Regression vectors from historical paper decisions.
   - Evaluation latency checks.
3. Bundle is signed and published with immutable version.
4. Promotion flow: `dev` -> `paper` -> `prod` with mandatory approval before `prod`.
5. Every promotion must include a tested rollback target.
6. Every activation and rollback must be actor-audited.

## Runtime Reliability Controls
1. `risk-service` startup readiness must fail until active bundle is loaded.
2. Bundle freshness watchdog alerts on stale bundle age.
3. OPA timeout budget is enforced in `risk-service` with bounded retry policy.
4. Backpressure/fail-safe behavior applies when policy latency breaches threshold.

## Security Controls for Policy Path
1. Accept bundles only from allowlisted endpoints.
2. Verify signatures before activation.
3. Keep activation/audit logs immutable.
4. Alert on unauthorized activation attempt or signature verification failure.

## Decision Explainability and Audit
- Every policy decision must carry:
  - `policy_version`
  - `policy_rule_set`
  - `matched_rule_ids`
  - `deny_reasons` (when denied)
  - `failure_mode` (for technical rejects)
- Emit normalized audit event: `policy.evaluations.audit.v1`.
- Operator-facing APIs/SSE should expose policy explanation fields for rejected orders.

## Reason Code Taxonomy
- `POLICY_DENY`
- `OPA_TIMEOUT`
- `OPA_UNAVAILABLE`
- `OPA_SCHEMA_ERROR`
- `BUNDLE_LOAD_ERROR`

## Related Contracts
- [Risk Service Contract](./contracts/risk-service.md)
- [Policy Bundle Contract](./contracts/policy-bundle-contract.md)
- [Policy Decision Audit Contract](./contracts/policy-decision-audit-contract.md)
- [Kafka Event Contracts](./KAFKA_EVENT_CONTRACTS.md)
