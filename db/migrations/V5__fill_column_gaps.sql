-- V5: Fill column gaps identified by Hibernate schema validation.
--
-- executions: side, created_at
-- routed_trade_events: instrument_id, route_topic
--
-- Each ADD COLUMN is a separate statement for H2 (test) compatibility.

ALTER TABLE executions ADD COLUMN IF NOT EXISTS side TEXT NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE executions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE routed_trade_events ADD COLUMN IF NOT EXISTS instrument_id TEXT;
ALTER TABLE routed_trade_events ADD COLUMN IF NOT EXISTS route_topic TEXT;
