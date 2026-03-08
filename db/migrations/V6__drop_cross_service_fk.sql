-- V6: Drop all cross-service FK constraints
--
-- Rationale: In a distributed multi-service architecture each service owns its domain schema.
-- Physical FKs that span service boundaries (e.g. risk_decisions → signals, broker_orders →
-- order_intents) create tight coupling that breaks when services are called independently
-- (smoke tests, direct gRPC calls, replays, async Kafka flows) without prior INSERTs into
-- the referenced tables that belong to a different service.
--
-- Correlation IDs are retained as plain TEXT columns.
-- Existing per-table indexes (e.g. idx_risk_decisions_signal, idx_broker_orders_intent)
-- already support efficient lookups without the constraints.

-- risk-service → agent-runtime-service
ALTER TABLE risk_decisions DROP CONSTRAINT IF EXISTS risk_decisions_signal_id_fkey;

-- ibkr-connector-service → order-service
ALTER TABLE broker_orders DROP CONSTRAINT IF EXISTS broker_orders_order_intent_id_fkey;
ALTER TABLE executions    DROP CONSTRAINT IF EXISTS executions_order_intent_id_fkey;

-- agent-runtime-service → event-processor-service
ALTER TABLE signals DROP CONSTRAINT IF EXISTS signals_trade_event_id_fkey;
