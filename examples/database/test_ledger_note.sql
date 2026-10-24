-- Run in a new disposable PostgreSQL database from this directory.
\set ON_ERROR_STOP on
CREATE TABLE ledger_entry (id bigint PRIMARY KEY, amount numeric(18,2) NOT NULL);
INSERT INTO ledger_entry VALUES (1, 12.34), (2, -12.34);
\ir V2__ledger_note.sql
DO $$ BEGIN
  IF (SELECT count(*) FROM ledger_entry) <> 2
     OR (SELECT amount FROM ledger_entry WHERE id=1) <> 12.34
     OR (SELECT amount FROM ledger_entry WHERE id=2) <> -12.34
     OR EXISTS (SELECT 1 FROM ledger_entry WHERE note IS NOT NULL)
  THEN RAISE EXCEPTION 'Migration did not preserve original records'; END IF;
END $$;
UPDATE ledger_entry SET note='reconciled' WHERE id=1;
DO $$ BEGIN
  IF (SELECT note FROM ledger_entry WHERE id=1) <> 'reconciled'
  THEN RAISE EXCEPTION 'New column is not usable'; END IF;
END $$;
