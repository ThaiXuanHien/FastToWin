FROM caddy:2.11.4-alpine

COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY webApp/build/dist/composeWebCompatibility/productionExecutable/ /srv/
COPY deploy/config.production.js /srv/config.js
