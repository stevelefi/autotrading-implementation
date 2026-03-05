# 01 Requirements and SLOs

## Functional Requirements
- Multi-agent signal ingestion.
- Pre-trade policy evaluation via OPA.
- Broker submission and callback processing.
- Reconciliation and forced freeze workflow.
- Monitoring API and operational dashboard.

## Non-Functional Requirements
- Strong consistency on order lifecycle.
- Idempotency for all write paths.
- Recoverability after connector/network faults.
- End-to-end traceability by `trace_id`.

## SLO Targets
- Signal-to-submit p95: &lt;= 300 ms (excluding broker fill latency).
- Monitoring freshness p95: &lt;= 2 seconds.
- Policy decision p95: &lt;= 20 ms (sidecar path).
- Unknown state MTTR target: &lt;= 15 minutes with runbook.

## Error Budget Policy
- P0 consistency violations have zero tolerance.
- Availability degrades are acceptable if required to preserve consistency.
