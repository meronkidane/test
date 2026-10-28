# CI maintenance — 2026-10-28

GitHub run 40718009008 passed all tests, image builds and the end-to-end smoke.
Its warnings identified deprecated Node 20 actions and a moving ubuntu-latest
image. The workflow now pins the Node 24-based v6 actions to commit SHAs, uses
ubuntu-24.04 explicitly, and bounds the job to 30 minutes. New CI verifies these
workflow-only changes. The separate AI review reports no code defects but remains
incomplete because its changed-file context budget omitted files; that reviewer
limitation is not an ingestion test failure.

# PR review follow-up — 2026-10-28

- Removed the committed source-reader password. Local setup now generates a distinct
  random SOURCE_PASSWORD; psql reads it from the environment and quotes it as an SQL
  literal during initialization. Compose requires the setting explicitly.
- New PostgreSQL data directories require SCRAM for TCP authentication. Existing
  databases require administrator-managed role/password and pg_hba.conf changes;
  initialization flags do not silently modify existing data directories.
- **26 tests passed, zero skips**, including a fresh disposable PostgreSQL bootstrap
  with a password containing quotes/backslashes, rejection of an incorrect password,
  SELECT-only permissions, and rejection of weak/reused API tokens and invalid
  idempotency keys. Worker cancellation/deadline and fencing tests remain passing.
- Rotated the local existing source-reader credential without deleting data, restarted
  the worker and reran the container smoke: **PASS**, 11 input rows, 8 output rows,
  all four sources, S3 Parquet readback, idempotency and cancellation verified.
- The initial PR's GitHub CI run 40716752958 completed successfully, including full
  Docker builds and container smoke. The follow-up commit triggers a new CI run.
- The remaining three review findings explicitly describe positive controls with
  "No change needed"; those controls are retained and covered by tests.

# Executed verification — 2026-10-28

- Python 3.12 / Java 21 host tests: **18 passed, zero skipped**. This includes
  a real local Spark 4.0.1 JVM and an isolated PostgreSQL test database.
- API and worker Docker targets: **built successfully** (Python 3.12 / Java 17).
- `docker compose config --quiet`: **passed**.
- API container health: **passed**, including startup without source/S3 credentials.
- Real container smoke: **passed**. Authenticated HTTP submission went through
  PostgreSQL claims to a separate Spark process. JDBC PostgreSQL and S3A CSV,
  JSON and Parquet reads produced S3 Parquet output and an accepted manifest.
- S3 output was read back by Spark: 8 rows, total amount 162.50, all four source
  identifiers, and latest duplicate amount 15.00. Input count 11; duplicates 3.
- Duplicate submission reused the run ID; cancellation reached CANCELLED and the
  worker observed its invalidated claim. See `smoke-result.json`.

The host's Docker data disk filled during image building. Verification used the
explicit `compose.test.yaml` ephemeral storage override after preserving existing
volumes. The final source was exercised with the already-built dependency images
and read-only `compose.verify.yaml` mounts after a small retry-status and smoke
assertion update. A full final-source image rebuild is delegated to the included CI;
it is not claimed as an additional local build.

A TestClient/httpx deprecation warning and Spark native/metrics logging warnings
were observed; tests still passed. The first attempts encountered the host's old
SPARK_HOME and Docker disk exhaustion; these were corrected before the passing run.

Not verified: real AWS credentials/IAM/KMS, production TLS/identity gateway,
large-volume throughput, remote Spark clusters, HA disaster recovery, CDC or a
consistent snapshot across sources. LocalStack is a protocol-compatible local
fixture, not evidence of production AWS deployment. CI has been configured but its
GitHub result must be checked separately after push.
