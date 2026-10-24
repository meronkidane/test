-- Existing ledger_entry(id bigint primary key, amount numeric(18,2) not null)
-- contains settled records. Preserve all records and add an optional note.
BEGIN;
SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '30s';
ALTER TABLE ledger_entry ADD COLUMN note text;
COMMIT;
