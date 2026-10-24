# Revalidation

In a disposable PostgreSQL database, run psql -v ON_ERROR_STOP=1 -f examples/database/test_ledger_note.sql. Delete the test database afterwards.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.
