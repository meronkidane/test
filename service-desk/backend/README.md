# Service desk API

Runnable Spring Boot 4.1.2 / Java 21 service for a tenant-scoped service-request workflow. Intended as a realistic review application; not a production deployment template.

Start: `DB_PASSWORD=local-only mvn spring-boot:run`. Verify: `mvn verify`. Docker: `docker build -t service-desk-api .`; pass DB_PASSWORD, OIDC_ISSUER and OIDC_JWKS and mount a persistent writable `/data` volume when running. H2 is for this single-instance local application; production storage/backup and HA are outside scope.

Requires a signed JWT with issuer configured above, audience `service-desk-api`, string `tenant`, stable subject, and top-level string-array `roles`. All authenticated tenant users can create/read requests; LEAD or ADMIN can update status. Tenant is never accepted from request input. Bearer-only stateless API; no cookie authentication. Use TLS outside localhost.

| Method | Path | Contract |
|---|---|---|
| GET | /api/requests?page=0&size=20&status=OPEN | `{items,page,size,hasNext}`; optional status, size 1–100 |
| GET | /api/requests/{uuid} | Item or tenant-safe 404 |
| POST | /api/requests | `{title,description,priority}`; 201 Item |
| PATCH | /api/requests/{uuid}/status | `{status,version}`; 200 Item, 409 on stale or invalid transition |

Item: id, title, description, priority (LOW/NORMAL/HIGH), status (OPEN/IN_PROGRESS/RESOLVED), requester (subject), version, createdAt and updatedAt (ISO timestamps). Legal transitions: OPEN → IN_PROGRESS → RESOLVED → OPEN. Creation is not idempotent; clients must not automatically replay ambiguous POST failures. Tenant members can see all requests in their tenant by design.

Companion frontend lives under `service-desk/frontend` in a separate PR targeting main. Combine both feature branches locally to run the complete application; neither PR merges the other implicitly.
