CREATE TABLE IF NOT EXISTS idempotency_records (
  idempotency_key TEXT PRIMARY KEY,
  payload_hash TEXT NOT NULL,
  status TEXT NOT NULL,
  response_snapshot TEXT,
  failure_reason TEXT,
  created_at_utc TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at_utc TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS outbox_events (
  event_id TEXT PRIMARY KEY,
  topic TEXT NOT NULL,
  partition_key TEXT,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at_utc TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at_utc TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS consumer_inbox (
  consumer_name TEXT NOT NULL,
  event_id TEXT NOT NULL,
  processed_at_utc TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE IF NOT EXISTS ingress_raw_events (
  raw_event_id TEXT PRIMARY KEY,
  ingress_event_id TEXT UNIQUE NOT NULL,
  trace_id TEXT NOT NULL,
  request_id TEXT NOT NULL,
  idempotency_key TEXT UNIQUE NOT NULL,
  source_type TEXT NOT NULL,
  source_event_id TEXT,
  agent_id TEXT,
  payload_json TEXT NOT NULL,
  ingestion_status TEXT NOT NULL,
  received_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS routed_trade_events (
  trade_event_id TEXT PRIMARY KEY,
  raw_event_id TEXT UNIQUE NOT NULL,
  ingress_event_id TEXT NOT NULL,
  trace_id TEXT NOT NULL,
  idempotency_key TEXT NOT NULL,
  agent_id TEXT,
  source_type TEXT NOT NULL,
  source_event_id TEXT,
  canonical_payload_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  routed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_intents (
  order_intent_id TEXT PRIMARY KEY,
  signal_id TEXT,
  agent_id TEXT NOT NULL,
  instrument_id TEXT,
  idempotency_key TEXT UNIQUE NOT NULL,
  side TEXT NOT NULL,
  qty INT NOT NULL,
  order_type TEXT NOT NULL,
  time_in_force TEXT NOT NULL,
  submission_deadline TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS order_ledger (
  order_intent_id TEXT PRIMARY KEY,
  state TEXT NOT NULL,
  state_version BIGINT NOT NULL,
  submission_deadline TIMESTAMPTZ NOT NULL,
  last_status_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS executions (
  exec_id TEXT PRIMARY KEY,
  order_intent_id TEXT NOT NULL,
  fill_qty INT NOT NULL,
  fill_price NUMERIC(18, 6) NOT NULL,
  fill_ts TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS system_controls (
  control_key TEXT PRIMARY KEY,
  control_value TEXT NOT NULL,
  actor_id TEXT NOT NULL,
  trace_id TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS reconciliation_runs (
  run_id TEXT PRIMARY KEY,
  status TEXT NOT NULL,
  mismatch_summary_json TEXT,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox_events (status, topic, created_at_utc);
CREATE INDEX IF NOT EXISTS idx_order_ledger_deadline ON order_ledger (state, submission_deadline);
CREATE INDEX IF NOT EXISTS idx_reconciliation_runs_status ON reconciliation_runs (status, started_at);
