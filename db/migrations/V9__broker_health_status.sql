-- V9: shared broker health state — written by ibkr-connector on every UP/DOWN transition,
--     read by ingress-gateway and order-service to gate order acceptance.
--     Using TEXT (not jsonb) for detail_json per schema convention (Rule 2).

CREATE TABLE IF NOT EXISTS broker_health_status (
    broker_id   TEXT        PRIMARY KEY,
    status      TEXT        NOT NULL,       -- 'UP' | 'DOWN' | 'UNKNOWN'
    detail_json TEXT,                       -- optional context (e.g. error message)
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_broker_health_status_updated_at
    ON broker_health_status (updated_at);

-- Seed the initial 'ibkr' row so readers always find exactly one row.
-- ON CONFLICT DO NOTHING makes this idempotent across re-applies.
INSERT INTO broker_health_status (broker_id, status, updated_at)
VALUES ('ibkr', 'UNKNOWN', now())
ON CONFLICT (broker_id) DO NOTHING;
