# Cross-Service SLO and Error Budget

## Latency SLOs
- Ingress accept to normalized publish: p95 &lt;= 2 seconds
- Signal generation to risk decision (`gRPC`): p95 &lt;= 40 ms
- Risk decision to order intent creation (`gRPC`): p95 &lt;= 30 ms
- Order intent to broker submit ack (`gRPC`): p95 &lt;= 50 ms
- Intent submit to first broker status (callback path): &lt;= 60 seconds hard deadline
- Fill to updated position snapshot (`Kafka` projection path): p95 &lt;= 1 second
- Kubernetes service discovery lookup latency: p95 &lt;= 25 ms within environment

## Availability SLOs
- Monitoring API read availability: 99.9%
- Trading command plane (`gRPC` mutations): 99.5%
- Policy evaluation path: 99.9% with fail-closed fallback
- Kubernetes discovery/config plane:
  - paper namespace baseline: 99.5%
  - production cluster profile: 99.95%

## Error Budget Policy
- P0 consistency violations consume full budget immediately and trigger release freeze.
- Timeout-induced unknown states are tracked as critical reliability debt.
- Discovery/config outages that bypass fail-closed controls consume full budget immediately.

## Alert Thresholds
- `unknown_pending_recon_open_count > 0` => P0
- `status_timeout_60s_count > 0` => P0
- `policy_eval_error_rate > 1% (5m)` => P1
- `opa_schema_error_count > 0 (5m)` => P1
- `policy_bundle_activation_failure_count > 0 (5m)` => P1
- `outbox_backlog_age > 120s` => P1
- `grpc_command_error_rate > 1% (5m)` => P1
- `k8s_dns_lookup_error_rate > 2% (5m)` => P1
- `config_apply_error_rate > 0 (5m)` => P1
