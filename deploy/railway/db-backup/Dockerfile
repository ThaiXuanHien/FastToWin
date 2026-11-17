# INF-03: daily encrypted Postgres backup to Cloudflare R2, run as a Railway cron service.
# pg_dump must not be older than the server (production runs PostgreSQL 18).
FROM postgres:18-alpine

RUN apk add --no-cache bash age aws-cli

COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh

USER postgres
ENTRYPOINT ["/usr/local/bin/backup.sh"]
