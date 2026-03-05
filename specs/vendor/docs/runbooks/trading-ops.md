# Trading Operations Runbook

## Startup Checklist
1. Verify IB Gateway connectivity.
2. Verify OPA health and active policy version.
3. Verify Kafka and Postgres health.
4. Run mandatory startup reconciliation.
5. Confirm trading mode before opening orders.

## Incident: Status Timeout > 60s
1. Confirm order moved to `UNKNOWN_PENDING_RECON`.
2. Confirm system switched to `FROZEN`.
3. Start reconciliation job.
4. Review mismatch report.
5. Resume only after unresolved count is zero and operator ack.

## Incident: Broker Disconnect
1. Freeze new opening orders.
2. Monitor connector reconnect attempts.
3. Run reconciliation after reconnect.
4. Keep frozen until reconciliation clean.

## Incident: OPA Unavailable
1. Keep fail-closed behavior for opening orders.
2. Restore sidecar or rollback policy bundle.
3. Validate decision endpoint health.
4. Resume with operator ack.

## Incident: OPA Schema Error (`OPA_SCHEMA_ERROR`)
1. Keep fail-closed behavior for opening orders.
2. Confirm active bundle and recent contract/schema changes.
3. Roll back to last-known-good policy bundle if mismatch started after activation.
4. Verify `opa.policy.input.v1` and `opa.policy.decision.v1` compatibility.
5. Resume only after validation checks pass and operator ack.

## Incident: Policy Bundle Signature/Activation Failure
1. Block activation and keep current active bundle.
2. Verify signer identity and checksum against release evidence.
3. Re-publish signed bundle or roll back to known-good version.
4. Record actor, bundle version, and reason in audit trail.

## Daily Close
1. Export daily performance report.
2. Check unresolved reconciliations.
3. Verify alerts and incident notes are attached to task tracker.
