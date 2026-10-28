CREATE TABLE source_orders(record_id text PRIMARY KEY,customer_id text,amount numeric(18,2),event_time timestamptz);
INSERT INTO source_orders VALUES ('db-1','customer-1',12.50,'2026-01-01T12:00:00Z'),('db-2','customer-2',30,'2026-01-02T12:00:00Z');
-- psql quotes the injected value as an SQL literal; never concatenate credentials.
\getenv source_password SOURCE_PASSWORD
CREATE ROLE ingestion_reader LOGIN PASSWORD :'source_password';
GRANT CONNECT ON DATABASE ingestion TO ingestion_reader;
GRANT USAGE ON SCHEMA public TO ingestion_reader;
GRANT SELECT ON source_orders TO ingestion_reader;
