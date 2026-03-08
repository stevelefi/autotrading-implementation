-- V7: Change JSONB columns to text for Spring Data JDBC compatibility.
-- Spring Data JDBC treats String as a simple type and passes it directly
-- as character varying; no converter chain runs. These columns store
-- opaque JSON blobs that never need PG JSONB operators, so text is correct.

ALTER TABLE ingress_raw_events ALTER COLUMN principal_json TYPE text;
ALTER TABLE ingress_errors     ALTER COLUMN principal_json TYPE text;
ALTER TABLE risk_decisions     ALTER COLUMN deny_reasons_json TYPE text;
ALTER TABLE risk_decisions     ALTER COLUMN matched_rule_ids_json TYPE text;
ALTER TABLE risk_events        ALTER COLUMN payload_json TYPE text;
ALTER TABLE signals            ALTER COLUMN raw_payload_json TYPE text;
