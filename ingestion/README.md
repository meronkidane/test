# Multi-source Spark ingestion

Two independently deployable services ingest PostgreSQL JDBC and S3 CSV, JSON,
and Parquet data into partitioned Parquet in an S3 bucket. This is a runnable batch
reference, not a claim of production readiness or exactly-once execution.

```mermaid
flowchart LR
    Client -->|Bearer token + idempotency key| API[Ingestion API]
    API --> Jobs[(PostgreSQL durable jobs)]
    Jobs -->|leased claim| Worker[Worker service]
    Worker -->|bounded subprocess| Spark[Spark application]
    JDBC[(Read-only PostgreSQL source)] --> Spark
    Files[(S3 CSV / JSON / Parquet)] --> Spark
    Spark --> Quality[Normalize / validate / deduplicate]
    Quality --> Output[(S3 attempt prefix: Parquet + quarantine + manifest)]
    Worker -->|claim-fenced completion| Jobs
    API -->|successful manifest reference| Client
```

## Run locally

Requires Docker Compose with at least 4 GB RAM and several GB free Docker disk.
Ports 8099, 55439 and 4569 are bound to loopback. Set `POSTGRES_HOST_PORT` in
`.env` to change the PostgreSQL host port if it conflicts with another local service.
The host binding supports local database tests and debugging; containers use
`postgres:5432` regardless of this setting. Run from this directory:

```sh
python3 scripts/configure.py  # creates .env once, mode 0600; never commit it
# Do not rerun configure.py over an existing environment.
docker compose up --build -d
# This client uses internal service addresses and the local demo credentials.
docker compose run --rm --no-deps -e API_URL=http://api:8080 worker python -m scripts.smoke
```

The seed service creates CSV/JSON/Parquet input objects. PostgreSQL initialization
creates two source rows and a SELECT-only source login whose random password is
generated in `.env` as SOURCE_PASSWORD. The initialization script reads it through
psql `\getenv` and SQL-literal quoting; no source password is committed. The smoke test submits one
four-source run, verifies idempotency, waits for completion, checks the S3 manifest
and partitioned objects, and tests cancellation. Expected: **11 input rows, 8 output
rows, 3 duplicates, 0 invalid rows**. Rerunning seed overwrites its known fixture
objects; it must not run against a real landing bucket.

API docs: <http://localhost:8099/docs>. Health: `/health` checks the control database.
A green API health response does not assert that Spark, sources or S3 are healthy.

To stop while preserving local job state: `docker compose down`. S3 fixture storage
in this LocalStack setup is ephemeral. `docker compose down -v` intentionally removes
local PostgreSQL state as well. Never use that command against a deployment to keep.

`compose.test.yaml` uses explicit tmpfs storage for ephemeral verification on hosts
with a full Docker data disk. It destroys test database/S3 state when containers are
recreated and is **not** a durable deployment configuration. `compose.verify.yaml`
adds read-only source mounts for developer verification against already-built images;
omit it for an immutable image deployment. Spark's bundled Snappy library requires
executable native-library mappings in its temporary directory, so the worker test
tmpfs explicitly permits that; no repository-supplied code is executed.

## Services and contracts

- **API (`app/api.py`)** authenticates a service token, checks dataset grants, pins
  an operator catalog snapshot, and transactionally submits a job. It has no Spark
  or S3 credentials requirement. API tokens are resolved from environment variables
  at startup; clients cannot choose their tenant.
- **Worker (`app/worker.py`)** claims one job at a time per replica, renews a lease,
  launches a separate Spark process, enforces a wall-clock deadline, and records
  success only when its claim is still live. Add replicas to increase concurrency.
  Workers are trusted platform services that process all tenants, not tenant-owned
  processes. Queue claims intentionally span tenants; API reads and cancellation
  remain tenant-scoped, and output prefixes include the job tenant. Do not give
  tenants worker database credentials. Dedicated tenant workers require a separate
  deployment and authorization design.
- **Spark task (`app/spark_job.py`, `app/pipeline.py`)** uses Spark DataFrame readers,
  JDBC fetch sizing, fixed schemas, distributed transformations and Parquet writers.
  It never collects the dataset to the Python driver. Local Compose uses `local[2]`;
  remote Spark masters require deployment-specific networking, credentials and
  dependencies, and are not validated by this reference.

```http
POST /v1/runs
Authorization: Bearer <service-token>
Idempotency-Key: orders-2026-10-28-001
Content-Type: application/json

{"dataset":"orders","sources":["postgres","csv","json","parquet"]}
```

Returns 202 with `id`, `status`, `attempts`, timestamps, `result`, `error_code` and
`duplicate`, plus a `Location` header. `GET /v1/runs/{id}` returns tenant-scoped
status. `POST /v1/runs/{id}/cancel` accepts queued/running jobs; terminal jobs return
409. Missing or cross-tenant IDs return 404. Unauthorized datasets return 403.
Unknown sources/extra request fields return 422. Reusing an idempotency key with
different source selections **or changed catalog configuration** returns 409.

## Data contract and quality

Every source supplies `record_id`, `customer_id`, `amount`, `event_time`. CSV header
order must match this schema. CSV and JSON structural errors fail the task rather
than silently dropping records. Parquet/JDBC must contain all required columns.
IDs are trimmed strings, amounts use decimal(18,2), timestamps are interpreted in
UTC. Missing IDs, malformed/nonnegative amount violations, and invalid timestamps
are quarantined with the original canonical input fields and a reason code.

A dataset defines `max_invalid_fraction`; an empty dataset, zero valid rows, or an
exceeded threshold fails the run. Quarantine may remain in the failed attempt prefix
for diagnosis; it is never exposed as successful output. Source row budgets fail
rather than silently truncate. These are result budgets, not a guarantee of bounded
source scanning cost; enforce database and infrastructure quotas as well.

Deduplication is by `(source_id, record_id)`, choosing the most recent event time and
then a deterministic SHA-256 tie breaker. IDs from separate sources stay distinct;
this is not entity resolution across systems. Output uses Snappy Parquet partitioned
by `event_date`, with configurable write partition count. Quarantine contains source
data and needs the same access controls and retention as the input.

## Retry, consistency and publication

Queue state is PostgreSQL-backed with `FOR UPDATE SKIP LOCKED` claims, 60-second
leases and 10-second heartbeats. Failure backoff is 5 seconds times the attempt
number; at most three attempts are allowed. Worker crashes are recovered after lease
expiry; expired attempts cannot heartbeat, complete, or overwrite a newer claim.
Cancellation invalidates the claim immediately and the worker terminates its process
group on its next heartbeat. A SIGKILL can leave an orphan process until its gateway
or container exits; its output remains fenced from successful publication.

Each attempt writes a unique prefix:

```text
s3a://curated/orders/<tenant>/<dataset>/runs/<run-id>/attempts/<claim-id>/
  data/event_date=2026-01-01/*.parquet
  quarantine/*.parquet           # only when invalid rows exist
  manifest.json
```

No shared `latest` file is overwritten. A manifest is written after Spark completes
its data writes; **only the manifest referenced by a SUCCEEDED API result is
published**. Consumers must not scan all attempt directories or use `_SUCCESS`
alone: failed/cancelled/stale attempts can leave objects, including complete-looking
manifests. The S3 write and database update are not a distributed transaction.
This provides one accepted attempt per logical run, not exactly-once computation.

Inputs are full snapshots read at execution time, not incremental CDC. Catalog
configuration is pinned; source contents are not. JDBC and multiple S3 sources do
not share a consistent point-in-time snapshot, and retries can see new data. Use
immutable landing prefixes or upstream exports for reproducible ingestion. Add
lifecycle cleanup for unreferenced attempts and incomplete multipart uploads only
after checking accepted manifests; do not expire successful output indiscriminately.

## Configuration and security boundaries

Operators edit `config/catalog.json`; API clients select only configured dataset and
source IDs. Arbitrary URLs, file paths, SQL, Spark options and executable code are
not accepted from requests. JDBC supports fixed PostgreSQL tables, with credentials
referenced by environment variable name, not embedded in the job row. The catalog
is trusted configuration and must be reviewed and deployed by administrators.

The two local principals deliberately share access to the same fixture source data;
job status and output prefixes are tenant separated. Real deployments should define
separate dataset/source grants and IAM scopes where source isolation is required.

S3A and boto3 use AWS credential-provider chains. LocalStack uses explicit public
`test` credentials; real AWS should use workload identity, least-privilege bucket
policies, encryption/KMS, TLS, private networking, secrets management and audited
access. The worker needs read access to authorized sources and write access to its
output prefix; the API needs neither. Source PostgreSQL credentials are SELECT-only.
The control database account in Compose is a bootstrap demo owner; production should
split migration ownership from API/worker DML privileges.

The worker never uses a shell or Docker socket to execute jobs. Logs expose job IDs
and bounded error codes, not source records, DSNs or raw Spark exceptions. Spark
child output is discarded by the worker to avoid credential/data leaks; investigate
with a secured diagnostic deployment when needed. Do not enable public Spark UIs or
unrestricted driver logs. API tokens are a reference service-to-service mechanism;
use a production identity gateway/OIDC, ingress rate limits and request-size limits
before exposing this API outside a private network.

## Build, versions and verification

Python 3.12, Java 17+ and Spark 4.0.1. Hadoop S3A 3.4.1 matches the bundled Hadoop
runtime; AWS SDK bundle and PostgreSQL JDBC are resolved through `connectors-pom.xml`.
Service and test lockfiles include the transitive Python versions actually tested.
API and worker are separate Dockerfile targets; the API image excludes Spark/JDBC.

```sh
python3.12 -m venv .venv
.venv/bin/pip install -r requirements-test.lock
# Do not inherit SPARK_HOME from a different Spark installation.
unset SPARK_HOME
export JAVA_HOME=/path/to/java17-or-21
export SPARK_LOCAL_IP=127.0.0.1
export PYSPARK_PYTHON="$(pwd)/.venv/bin/python"
# Use a dedicated test DB: the tests TRUNCATE ingestion_jobs.
export TEST_DATABASE_URL=postgresql://user:password@localhost:55439/ingestion_test
.venv/bin/python -m pytest -q
```

Without TEST_DATABASE_URL, PostgreSQL/API tests are explicitly skipped; this is not
full verification. Tests cover concurrent idempotency/claims, fencing, retries,
tenant isolation, cancellation/deadlines, real Spark normalization/deduplication,
quarantine and quality failure. Container smoke covers real JDBC plus S3A reads and
S3 Parquet writes. See `VERIFICATION.md` for executed results and limitations.

Design references: [Spark JDBC](https://spark.apache.org/docs/4.0.1/sql-data-sources-jdbc.html),
[Spark cloud storage semantics](https://spark.apache.org/docs/4.0.1/cloud-integration.html),
[Hadoop S3A endpoints](https://hadoop.apache.org/docs/r3.4.1/hadoop-aws/tools/hadoop-aws/connecting.html).

### Existing local environments

New environments get SOURCE_PASSWORD from `scripts/configure.py`. For an existing
`.env`, add a newly generated secret without replacing the other credentials. The
PostgreSQL init script runs only for an empty data directory: changing `.env` does
**not** rotate an existing database role. Rotate `ingestion_reader` through an
administrator-controlled credential-management process and restart workers with
the matching secret. Do not delete a persistent volume as a credential-rotation step.
