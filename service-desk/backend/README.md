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

## Review follow-up: health, storage and credentials

Only `GET /actuator/health` is deliberately anonymous for infrastructure probes;
it returns the aggregate status without component or database details. All other
paths/methods require authentication, and API roles and tenant claims remain enforced.
Regression tests cover anonymous reads/writes, health-prefix paths, and role/tenant denial.

The container uses H2 only for this single-instance local demo. `/data` is a declared
volume and is owned by UID/GID 10001 so the non-root process can write its database.
The ownership change is required, not a privilege grant to the application. Reuse a
**named volume** across container replacements; an anonymous volume alone does not
provide a reliable reconnect/backup strategy:

```sh
docker volume create service-desk-data
docker run --rm -p 127.0.0.1:8080:8080 \
  --mount source=service-desk-data,target=/data \
  --mount type=bind,src="$(pwd)/secrets",dst=/run/secrets,readonly \
  service-desk-api
```

Create `secrets/DB_PASSWORD` outside version control using a secret manager or a
protected local file readable by UID 10001. Spring Boot imports `/run/secrets/` as
a configuration tree; files named `DB_URL`, `OIDC_ISSUER`, and `OIDC_JWKS` can also
supply deployment configuration. Environment variables remain a supported local
alternative; reading one does not itself publish its value. Do not put passwords
in URLs, image layers, shell history, or logs. Do not use this H2 demo as a
multi-replica production database; provision external storage, migration support,
and tested backups before a production deployment.

Reference: [Spring Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).
