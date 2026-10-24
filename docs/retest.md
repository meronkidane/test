# Revalidation

Run db/review_fixture.sql then db/test_review_fixture.sql against a disposable PostgreSQL database; never a shared or production database.

Run the strict review against the latest commit. An automated success still requires independent human approval. Do not suppress findings to obtain a passing result; investigate concrete defects and document false positives.
