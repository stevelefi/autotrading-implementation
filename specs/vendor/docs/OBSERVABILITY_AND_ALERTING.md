# Observability and Alerting

## Observability Goals
1. Detect consistency threats before they become execution loss.
2. Detect safety-control failures in near real time.
3. Provide enough evidence for deterministic incident reconstruction.

## Core Metrics (Must-Have)
Execution correctness:
- order submit latency (`p50/p95/p99`)
- first broker status timeout count (`>60s`)
- `UNKNOWN_PENDING_RECON` active count
- duplicate idempotency suppression count
- reconciliation unresolved mismatch count

Platform health:
- kafka lag by consumer group
- outbox backlog depth and publish latency
- db pool usage and query latency
- OPA evaluation latency and failure rate
- OPA schema validation failure count
- policy bundle freshness age and active version
- policy bundle activation failure count
- connector session state and reconnect count

## Structured Logging
All services must emit JSON logs with:
- `trace_id`
- `agent_id` (when applicable)
- `order_intent_id` (when applicable)
- `instrument_id` (when applicable)
- `event_id`/`request_id`
- `service`
- `severity`

## Distributed Tracing
1. Use OpenTelemetry spans end-to-end across all services.
2. Correlate broker callbacks to originating order intent.
3. Include control actions (`kill-switch`, `reconcile`, `resume`) in trace flow.

## Alert Taxonomy
P0 (immediate paging):
- `UNKNOWN_PENDING_RECON > 0` during trading window
- connector split-brain or lease violation
- OPA unavailable while market is open
- policy bundle activation attempted with invalid signature
- control-plane write/audit failure on critical action

P1 (urgent response):
- reconciliation mismatch above threshold
- sustained kafka lag above threshold
- sustained DB saturation or timeout trend
- status timeout trend breach
- OPA timeout trend breach
- OPA schema mismatch count breach
- policy bundle freshness breach

P2 (degradation watch):
- rising retry counts
- dashboard freshness delay
- non-critical service error spikes

## Dashboard Minimums
1. Order lifecycle dashboard: submit, status, fill, timeout, unknown counts.
2. Risk/policy dashboard: decision latency, reject rates, OPA health.
3. Policy governance dashboard: active bundle version, bundle freshness, activation failures, rollback events.
4. Broker connectivity dashboard: connection state, callback delay, reconnects.
5. Data reliability dashboard: outbox backlog, lag, replay health.
6. Control-plane dashboard: kill-switch state, trading mode, recent operator actions.

## Alert-to-Runbook Mapping
Every P0/P1 alert must map to a runbook section with:
1. immediate containment actions,
2. triage queries and dashboards,
3. recovery criteria,
4. post-incident evidence checklist.

## Related References
- [Cross-Service SLO and Error Budget](./contracts/cross-service-slo.md)
- [Trading Ops Runbook](./runbooks/trading-ops.md)
- [Trading Best Practices Baseline](./TRADING_BEST_PRACTICES_BASELINE.md)
