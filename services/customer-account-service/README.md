# Customer account service

Java 21 / Spring Boot 3.5 service reading customer profiles and accounts from PostgreSQL.
This is a standalone service in the test repository; it does not modify the review platform.

## API

All business endpoints require a bearer JWT with `accounts:read` scope. The trusted
issuer's `sub` maps to `customers.auth_subject`; clients cannot choose another customer.

- `GET /api/v1/me` — customer ID and display name; unknown customer returns 404.
- `GET /api/v1/me/accounts?limit=50&after=<uuid>` — bounded keyset page (1–100),
  `items` and `nextCursor`; absent cursor means the first page. Unknown customers get
  an empty page. Pagination is a live view, not a cross-request database snapshot.
- `GET /api/v1/me/accounts/{id}` — account ID, currency, exact decimal balance and status;
  absent or other-customer accounts return the same 404.
- `GET /actuator/health` — health without database details.

The service returns no full account numbers, email addresses or authentication subjects.
Consumers must parse balances as decimals, not binary floating point. Database errors
return sanitized RFC 9457 problem details with HTTP 503. Invalid cursors/limits return 400.
Unauthenticated requests return 401; missing scope returns 403. All writes are denied.

## Build and test

With Java 21 and Docker running:

```sh
./mvnw -B verify spotless:check
```

Tests provision PostgreSQL with Testcontainers and run Flyway migrations. Tests cover
ownership, pagination, precise balances, validation, authentication/scope, SQL injection,
missing records and sanitized database failure. JWT authentication is simulated in
endpoint tests; a mocked decoder also verifies invalid bearer tokens are rejected.
Real identity-provider discovery/signature/issuer/audience integration needs deployment validation.

## Run

Supply `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_ISSUER_URI` and `JWT_AUDIENCE`
through your secret/configuration manager. Example JDBC shape:
`jdbc:postgresql://localhost:5432/customer_accounts?sslmode=verify-full`.
Use TLS for remote PostgreSQL and HTTPS at ingress. The issuer must issue a unique,
stable subject for each customer and include this service's audience.

Apply `src/main/resources/db/migration/V1__customer_accounts.sql` through your
migration pipeline/Flyway using a separate DDL role. Migrations are disabled by default;
`FLYWAY_ENABLED=true` is available for a disposable local database. This service owns a
new schema; mapping to an existing enterprise schema needs an explicit migration design.
Do not enable migrations under the production runtime role. Grant the runtime role
CONNECT, schema USAGE and SELECT only on `customers` and `accounts`.

```sh
java -jar target/customer-account-service-0.1.0-SNAPSHOT.jar
curl -H "Authorization: Bearer ${ACCESS_TOKEN}" http://localhost:8080/api/v1/me/accounts
```

Provision customer subject mappings and account data through a separate trusted writer.
No seeded PII, passwords or tokens are committed. Spring Security validates signatures,
issuer, timestamps and configured audience using its standard resource-server support:
https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html

Deployment still requires identity-provider integration, database role provisioning,
backup/retention policies, TLS, centralized telemetry and organization-specific controls.
