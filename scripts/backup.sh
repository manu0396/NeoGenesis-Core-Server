#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-}"
if [[ -z "$OUTPUT_PATH" ]]; then
  echo "Usage: scripts/backup.sh /path/to/backup.dump"
  exit 1
fi

DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ -z "$DB_URL" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "DB_URL, DB_USER, and DB_PASSWORD must be set"
  exit 1
fi

export PGPASSWORD="$DB_PASSWORD"

HOST=$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^/:]+):?([0-9]*)/.*#\1#')
PORT=$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://[^/:]+:([0-9]+)/.*#\1#')
DBNAME=$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://[^/]+/(.*)#\1#')

if [[ "$PORT" == "$DB_URL" ]]; then
  PORT="5432"
fi

pg_dump -h "$HOST" -p "$PORT" -U "$DB_USER" -F c -f "$OUTPUT_PATH" "$DBNAME"
echo "Backup written to $OUTPUT_PATH"
