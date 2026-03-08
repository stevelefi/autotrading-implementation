-- V3: Add retry backoff columns to outbox_events.
--
-- next_retry_at: when the poller should next attempt this FAILED event.
--               NULL means "eligible immediately" (applies to NEW and freshly-FAILED rows).
--
-- These columns are only meaningful for ingress-gateway-service, which is the
-- sole service that still uses the transactional outbox as a Kafka fallback.
-- All other services removed the outbox in favour of direct Kafka publish.

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ NULL;
