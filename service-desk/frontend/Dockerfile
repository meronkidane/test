# Node 22 is a supported LTS line; update its patch image during normal rebuilds.
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
# npm ci rejects manifest/lockfile mismatches; keep both in this cache layer.
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/console/browser /usr/share/nginx/html
# Metadata only; the deployment chooses host bindings and TLS termination.
EXPOSE 80
