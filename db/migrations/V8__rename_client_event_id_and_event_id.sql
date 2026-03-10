-- V8: Rename idempotency_key -> client_event_id and ingress_event_id -> event_id
--
-- Design change: 2-ID model (client_event_id + event_id/OTel trace_id).
-- First-write-wins: duplicate client_event_id always returns the original 202.
-- event_id is the OTel trace_id (32-char hex) — unified across Tempo, Loki, DB.

-- idempotency_records: idempotency_key → client_event_id
ALTER TABLE idempotency_records RENAME COLUMN idempotency_key TO client_event_id;

-- ingress_raw_events: ingress_event_id → event_id, idempotency_key → client_event_id
ALTER TABLE ingress_raw_events RENAME COLUMN ingress_event_id TO event_id;
ALTER TABLE ingress_raw_events RENAME COLUMN idempotency_key  TO client_event_id;

-- ingress_errors: ingress_event_id → event_id, idempotency_key → client_event_id
ALTER TABLE ingress_errors RENAME COLUMN ingress_event_id TO event_id;
ALTER TABLE ingress_errors RENAME COLUMN idempotency_key  TO client_event_id;

-- routed_trade_events: ingress_event_id → event_id, idempotency_key → client_event_id
ALTER TABLE routed_trade_events RENAME COLUMN ingress_event_id TO event_id;
ALTER TABLE routed_trade_events RENAME COLUMN idempotency_key  TO client_event_id;

-- signals: idempotency_key → client_event_id
ALTER TABLE signals RENAME COLUMN idempotency_key TO client_event_id;

-- order_intents: idempotency_key → client_event_id
ALTER TABLE order_intents RENAME COLUMN idempotency_key TO client_event_id;

-- Rebuild any indexes that referenced the old column names.
-- PostgreSQL RENAME COLUMN automatically updates indexes defined on that column,
-- but explicit CREATE INDEX IF NOT EXISTS guards are added for clarity.
DROP INDEX IF EXISTS idx_ingress_raw_events_idempotency_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_ingress_raw_events_client_event_id
    ON ingress_raw_events (client_event_id);
