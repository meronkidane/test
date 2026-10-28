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
