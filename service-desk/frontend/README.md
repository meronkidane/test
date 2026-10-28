# Fieldwork service desk

Angular standalone application for the companion `service-desk/backend` API. Includes tenant queue, status filters and pagination, request creation, detail view and role-aware lifecycle updates. Server authorization remains authoritative. Reactively validated forms, escaped text rendering, explicit loading/error/success feedback, and version-conflict recovery are included.

`npm ci && npm run build`; `npm run e2e` against the running app. Tests require `DESK_URL` (default http://localhost:8098) and `REVIEW_LOGIN_PASSWORD`, with alice (LEAD) and outsider (other tenant) in the local OIDC realm. Node 22 LTS recommended. No credentials are checked in.

Serve `dist/console/browser` with `nginx.conf`; `/api` proxies to `service-desk-backend:8080`. Runtime `config.json` supplies API and IdP settings. Default local login: realm review at http://localhost:8097, public client service-desk-ui, redirect/origin http://localhost:8098. Configure PKCE S256, audience service-desk-api and tenant/roles claims in that client. Access tokens stay in memory and refresh before each API request. No automatic mutation retries.

Combine this branch with the separate backend PR to run both. Use Dockerfiles to build images and attach them to the same network; publish UI port 80 on loopback with `-p 127.0.0.1:8098:80`. Configure the backend issuer to the browser-facing issuer and JWKS to the container-reachable IdP URL. TLS, production IdP, durable external database, observability and deployment policy are separate operational work.

Build tooling uses Angular CLI/build 21.2.28 with a scoped Undici 7.30.1 patch override for the published 7.30.0 advisories. Production bundle build and browser workflow were revalidated after the override; npm audit reported zero known findings at submission.

## Docker review decisions

- **Node version:** Node 22 is an LTS release, with maintenance support scheduled
  through April 2027. Keep the supported major rather than an unbounded `latest`
  image; rebuild for patch updates and plan the next LTS migration before EOL.
  [Official release schedule](https://github.com/nodejs/Release#release-schedule).
- **Dependency manifests:** Copy `package.json` and `package-lock.json` together,
  then run `npm ci`. Splitting COPY steps does not improve consistency; `npm ci`
  rejects mismatches. Explicit filenames make a missing lockfile fail at COPY.
  [npm ci contract](https://docs.npmjs.com/cli/commands/npm-ci/).
- **Port exposure:** `EXPOSE 80` is image metadata and does not publish a host port.
  The local example binds only loopback. Production deployments must configure
  TLS termination, ingress access and firewall rules at the deployment boundary.
  [Docker EXPOSE reference](https://docs.docker.com/reference/dockerfile/#expose).

The review's proposed separate COPY instructions and removal of EXPOSE would not
fix security defects, so those behavior changes were not made.

Token roles are runtime-validated as an array of strings before management controls
are displayed; malformed or missing claims disable those controls. The hint is
recomputed after token refresh. Run `npm test` for malformed-claim regressions.
This is presentation logic: the companion backend validates JWT signature, issuer
and audience and enforces LEAD/ADMIN using `@PreAuthorize` on status transitions.
Backend denial tests exercise direct API requests, bypassing the frontend.
