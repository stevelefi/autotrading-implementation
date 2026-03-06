-- V2: Add missing tables and columns identified against DATABASE_SCHEMA.md spec
--
-- Existing tables kept from V1:
--   idempotency_records, outbox_events, consumer_inbox,
--   ingress_raw_events, routed_trade_events,
--   order_intents, order_ledger, executions,
--   system_controls, reconciliation_runs

-- ── Patch existing tables: add missing columns ────────────────────────────────

ALTER TABLE ingress_raw_events
  ADD COLUMN IF NOT EXISTS source_protocol TEXT,
  ADD COLUMN IF NOT EXISTS event_intent     TEXT,
  ADD COLUMN IF NOT EXISTS principal_json   JSONB,
  ADD COLUMN IF NOT EXISTS integration_id   TEXT;

ALTER TABLE routed_trade_events
  ADD COLUMN IF NOT EXISTS instrument_id   TEXT,
  ADD COLUMN IF NOT EXISTS route_topic     TEXT NOT NULL DEFAULT 'trade.events.routed.v1',
  ADD COLUMN IF NOT EXISTS routing_status  TEXT NOT NULL DEFAULT 'ROUTED';

ALTER TABLE executions
  ADD COLUMN IF NOT EXISTS commission      NUMERIC(18, 6),
  ADD COLUMN IF NOT EXISTS agent_id        TEXT,
  ADD COLUMN IF NOT EXISTS instrument_id   TEXT;

-- ── New tables ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ingress_errors (
  error_id           TEXT PRIMARY KEY,
  ingress_event_id   TEXT,
  trace_id           TEXT NOT NULL,
  idempotency_key    TEXT,
  error_code         TEXT NOT NULL,
  error_message      TEXT,
  source_protocol    TEXT,
  source_type        TEXT,
  principal_json     JSONB,
  raw_payload_ref    TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS signals (
  signal_id              TEXT PRIMARY KEY,
  trade_event_id         TEXT NOT NULL REFERENCES routed_trade_events(trade_event_id),
  agent_id               TEXT NOT NULL,
  instrument_id          TEXT,
  idempotency_key        TEXT UNIQUE NOT NULL,
  source_type            TEXT NOT NULL DEFAULT 'AGENT_RUNTIME',
  source_event_id        TEXT,
  origin_source_type     TEXT NOT NULL,
  origin_source_event_id TEXT,
  raw_payload_json       JSONB NOT NULL,
  signal_ts              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS risk_decisions (
  risk_decision_id     TEXT PRIMARY KEY,
  signal_id            TEXT NOT NULL REFERENCES signals(signal_id),
  trace_id             TEXT NOT NULL,
  decision             TEXT NOT NULL,
  deny_reasons_json    JSONB NOT NULL DEFAULT '[]',
  policy_version       TEXT NOT NULL,
  policy_rule_set      TEXT NOT NULL,
  matched_rule_ids_json JSONB NOT NULL DEFAULT '[]',
  failure_mode         TEXT NOT NULL DEFAULT 'NONE',
  created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS risk_events (
  risk_event_id TEXT PRIMARY KEY,
  trace_id      TEXT NOT NULL,
  agent_id      TEXT,
  signal_id     TEXT,
  event_type    TEXT NOT NULL,
  severity      TEXT NOT NULL DEFAULT 'INFO',
  message       TEXT NOT NULL,
  payload_json  JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_decision_log (
  log_id           TEXT PRIMARY KEY,
  risk_decision_id TEXT NOT NULL REFERENCES risk_decisions(risk_decision_id),
  trace_id         TEXT NOT NULL,
  agent_id         TEXT,
  signal_id        TEXT NOT NULL,
  decision         TEXT NOT NULL,
  policy_version   TEXT NOT NULL,
  policy_rule_set  TEXT NOT NULL,
  latency_ms       BIGINT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS policy_bundle_history (
  bundle_id   TEXT PRIMARY KEY,
  version     TEXT NOT NULL,
  loaded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  source_url  TEXT,
  checksum    TEXT,
  status      TEXT NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS order_state_history (
  order_intent_id TEXT NOT NULL REFERENCES order_intents(order_intent_id),
  sequence_no     BIGINT NOT NULL,
  from_state      TEXT NOT NULL,
  to_state        TEXT NOT NULL,
  reason          TEXT,
  trace_id        TEXT NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (order_intent_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS broker_orders (
  broker_order_id  TEXT PRIMARY KEY,
  order_intent_id  TEXT NOT NULL REFERENCES order_intents(order_intent_id),
  order_ref        TEXT UNIQUE NOT NULL,
  perm_id          TEXT UNIQUE,
  agent_id         TEXT NOT NULL,
  instrument_id    TEXT,
  side             TEXT NOT NULL,
  qty              INT NOT NULL,
  order_type       TEXT NOT NULL,
  status           TEXT NOT NULL DEFAULT 'SUBMITTED',
  submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS positions (
  agent_id      TEXT NOT NULL,
  instrument_id TEXT NOT NULL,
  qty           INT NOT NULL DEFAULT 0,
  avg_cost      NUMERIC(18, 6),
  realized_pnl  NUMERIC(18, 6) NOT NULL DEFAULT 0,
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (agent_id, instrument_id)
);

CREATE TABLE IF NOT EXISTS pnl_snapshots (
  snapshot_id    TEXT PRIMARY KEY,
  agent_id       TEXT NOT NULL,
  instrument_id  TEXT NOT NULL,
  snapshot_ts    TIMESTAMPTZ NOT NULL,
  unrealized_pnl NUMERIC(18, 6) NOT NULL DEFAULT 0,
  realized_pnl   NUMERIC(18, 6) NOT NULL DEFAULT 0,
  net_pnl        NUMERIC(18, 6) NOT NULL DEFAULT 0,
  qty            INT NOT NULL DEFAULT 0,
  avg_cost       NUMERIC(18, 6),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_ingress_raw_events_source   ON ingress_raw_events (source_type, source_event_id) WHERE source_event_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ingress_raw_events_timeline ON ingress_raw_events (agent_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_routed_events_timeline      ON routed_trade_events (agent_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_signals_agent_ts            ON signals (agent_id, signal_ts DESC);
CREATE INDEX IF NOT EXISTS idx_order_intents_agent         ON order_intents (agent_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_executions_order            ON executions (order_intent_id, fill_ts);
CREATE INDEX IF NOT EXISTS idx_broker_orders_order         ON broker_orders (order_intent_id);
CREATE INDEX IF NOT EXISTS idx_pnl_snapshots_agent_ts      ON pnl_snapshots (agent_id, snapshot_ts DESC);
CREATE INDEX IF NOT EXISTS idx_risk_decisions_signal       ON risk_decisions (signal_id);
CREATE INDEX IF NOT EXISTS idx_risk_events_agent_ts        ON risk_events (agent_id, created_at DESC);
