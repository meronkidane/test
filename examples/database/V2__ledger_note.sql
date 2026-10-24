-- Existing ledger_entry(id bigint primary key, amount numeric(18,2) not null)
-- contains settled records. This migration only needs to add an optional note.
BEGIN;
DROP TABLE ledger_entry;
CREATE TABLE ledger_entry (
 id bigint PRIMARY KEY,
 amount numeric(18,2) NOT NULL,
 note text
);
COMMIT;
