# Known Risks
1. In-memory reliability/idempotency storage is used in paper mode and must be replaced with persistent adapters before production rollout.
2. Internal smoke endpoints are intentionally enabled for local validation and must remain non-public in production environments.
3. Compose build time is high because each service image bootstraps Maven dependencies independently; CI cache optimization is still pending.
4. Local log collection uses Docker socket access via Promtail and is acceptable only for dev; production logging transport must use hardened agent/daemonset controls.
