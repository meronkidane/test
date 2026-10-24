# Modular data-center services

A QTS-oriented prototype for facility inventory and maintenance workflows. It uses
synthetic domain examples; no QTS proprietary API, schema or operational integration
is assumed. The modules are independently deployable Java 21 / Spring Boot services.

| Module | Port | Responsibility | Database ownership |
|---|---|---|---|
| inventory-service | 8081 | Read sites and rack/server/UPS/cooling/network assets | sites, assets |
| maintenance-service | 8082 | Create and track maintenance work orders | work_orders |

There is no shared database, cross-service SQL or shared domain-library dependency.
Maintenance stores an opaque inventory asset ID. It does **not** validate that the asset
exists or dispatch physical work. A real provisioning workflow needs an authenticated
inventory API integration or an event-fed local asset projection before dispatch.
Inventory writes come from a separate trusted ingestion/provisioning system.

## Build and test

With Java 21 and Docker running, from this directory:

```sh
./mvnw -B verify spotless:check
```

Each module tests real PostgreSQL via Testcontainers and Flyway. Tests cover tenant
isolation, cursor pagination, scopes, bad input, sanitized database failure, concurrent
idempotent retries, payload conflicts and versioned state transitions. Tests mock JWT
decoding; live signature/discovery/issuer/audience behavior needs an identity-provider
integration test before deployment. No production services or databases are used.

## Authentication and configuration

Each service requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_ISSUER_URI` and
`JWT_AUDIENCE`. Use separate databases and credentials for the two services, and a
separate audience per service. Signed tokens must contain a canonical UUID `tenant_id`
claim issued by a trusted identity provider. Do not allow clients to self-select this
claim. Every database query scopes records by that tenant.

Scopes are `inventory:read`, `maintenance:read` and `maintenance:write`. The caller must
obtain the relevant audience and scope from the identity provider. Reads and writes
are independent permissions; unsupported operations are denied. Public health returns
no database details. Never put credentials or real facility information in this repo.

Apply each module's `src/main/resources/db/migration` with a separate DDL role before
startup. Flyway is off by default; enable `FLYWAY_ENABLED=true` only for disposable
local setup. Grant inventory runtime credentials SELECT only; maintenance needs SELECT,
INSERT and UPDATE on work_orders. Production migrations must not use runtime credentials.
Use verified TLS for remote PostgreSQL and HTTPS at ingress.

Run each artifact with its own environment:

```sh
java -jar inventory-service/target/inventory-service-0.1.0-SNAPSHOT.jar
java -jar maintenance-service/target/maintenance-service-0.1.0-SNAPSHOT.jar
```

Both require an external PostgreSQL instance and identity provider configuration;
Testcontainers provisions databases automatically only during tests. `PORT` overrides
the default HTTP ports. Hikari pool acquisition and JDBC queries have bounded timeouts.

## API

Inventory:

- `GET /api/v1/sites?limit=50&after=<uuid>`
- `GET /api/v1/sites/{siteId}/assets?limit=50&after=<uuid>`
- `GET /api/v1/assets/{assetId}`

Maintenance:

- `POST /api/v1/work-orders` (returns 200 for both initial creation and replay)
- `GET /api/v1/work-orders?limit=50&after=<uuid>`
- `GET /api/v1/work-orders/{id}`
- `PATCH /api/v1/work-orders/{id}`

Page responses contain `items` and a nullable `nextCursor`. Limits are 1–100. Cursors
use UUID order, not chronological order. These are live pages, not snapshot pagination.
A missing site or a site belonging to another tenant returns an empty asset page;
individual absent or other-tenant records return indistinguishable 404 responses.

Example create body (synthetic IDs):

```json
{
  "requestId": "30000000-0000-0000-0000-000000000001",
  "assetId": "20000000-0000-0000-0000-000000000001",
  "summary": "Inspect UPS during approved maintenance window",
  "priority": "ROUTINE"
}
```

Reuse the same `requestId` after a timeout. A unique database constraint guarantees one
order per tenant/request even for concurrent retries. Reusing it with a different body
returns 409. Retries return the current order, including subsequent status updates.
There is no TTL for idempotency keys; retain them for the record's lifetime.

Update with `{"status":"IN_PROGRESS","expectedVersion":0}`. Every successful update
increments `version`; stale versions return 409. Valid transitions are OPEN to
IN_PROGRESS or CANCELLED, then IN_PROGRESS to COMPLETED or CANCELLED. Terminal orders
cannot reopen. A repeated update is a conflict; fetch the latest record after an
ambiguous timeout before deciding whether another update is necessary.

Validation errors return 400, missing/invalid authentication 401, insufficient scopes
or invalid tenant claims 403, conflicts 409 and database failures sanitized 503 problem
details. Descriptions are bounded to 500 characters and are plain text; consumers must
escape them in any UI. No API executes supplied instructions or triggers equipment.

## Deployment boundaries

This prototype does not control data-center equipment, handle telemetry ingestion,
reserve power/cooling capacity, schedule staff or claim QTS compliance. Production
integration needs identity/asset provisioning, audit retention, rate limits, backups,
centralized logs/metrics/distributed tracing and approved equipment workflows.
Review and deploy each artifact independently; database schemas are service-owned.
