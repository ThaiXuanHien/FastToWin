#!/usr/bin/env bash
# Dumps the database, encrypts it for BACKUP_AGE_RECIPIENT (only the owner holds the private key),
# and uploads the dump plus its SHA-256 to R2. Old copies are removed by the bucket lifecycle rule.
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL is required}"
: "${BACKUP_AGE_RECIPIENT:?BACKUP_AGE_RECIPIENT (age public key) is required}"
: "${R2_ENDPOINT:?R2_ENDPOINT is required}"
: "${R2_BUCKET:?R2_BUCKET is required}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID (R2 token key id) is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY (R2 token secret) is required}"

name="fasttowin-$(date -u +%Y-%m-%dT%H%MZ).dump.age"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

pg_dump --format=custom --no-owner --no-privileges --dbname="$DATABASE_URL" \
  | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$workdir/$name"

size="$(wc -c < "$workdir/$name")"
if [ "$size" -lt 4096 ]; then
  echo "Backup is only $size bytes; refusing to upload a likely empty dump." >&2
  exit 1
fi
(cd "$workdir" && sha256sum "$name" > "$name.sha256")

export AWS_DEFAULT_REGION=auto
for file in "$name" "$name.sha256"; do
  aws s3 cp "$workdir/$file" "s3://$R2_BUCKET/postgres/$file" \
    --endpoint-url "$R2_ENDPOINT" --only-show-errors
done
echo "Uploaded postgres/$name ($size bytes) to $R2_BUCKET."
