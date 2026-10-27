# Fieldwork service desk

Angular standalone application for the companion `service-desk/backend` API. Includes tenant queue, status filters and pagination, request creation, detail view and role-aware lifecycle updates. Server authorization remains authoritative. Reactively validated forms, escaped text rendering, explicit loading/error/success feedback, and version-conflict recovery are included.

`npm ci && npm run build`; `npm run e2e` against the running app. Tests require `DESK_URL` (default http://localhost:8098) and `REVIEW_LOGIN_PASSWORD`, with alice (LEAD) and outsider (other tenant) in the local OIDC realm. Node 22 LTS recommended. No credentials are checked in.

Serve `dist/console/browser` with `nginx.conf`; `/api` proxies to `service-desk-backend:8080`. Runtime `config.json` supplies API and IdP settings. Default local login: realm review at http://localhost:8097, public client service-desk-ui, redirect/origin http://localhost:8098. Configure PKCE S256, audience service-desk-api and tenant/roles claims in that client. Access tokens stay in memory and refresh before each API request. No automatic mutation retries.

Combine this branch with the separate backend PR to run both. Use Dockerfiles to build images and attach them to the same network; publish UI port 80 as 8098. Configure the backend issuer to the browser-facing issuer and JWKS to the container-reachable IdP URL. TLS, production IdP, durable external database, observability and deployment policy are separate operational work.

Build tooling uses Angular CLI/build 21.2.28 with a scoped Undici 7.30.1 patch override for the published 7.30.0 advisories. Production bundle build and browser workflow were revalidated after the override; npm audit reported zero known findings at submission.
