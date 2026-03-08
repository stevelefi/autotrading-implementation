-- V4: Add broker_order_id to executions table.
-- ExecutionEntity maps this column; was missing from the V1 baseline.

ALTER TABLE executions ADD COLUMN IF NOT EXISTS broker_order_id TEXT;
