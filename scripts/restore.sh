#!/usr/bin/env bash
set -euo pipefail

INPUT_PATH="${1:-}"
if [[ -z "$INPUT_PATH" ]]; then
  echo "Usage: scripts/restore.sh /path/to/backup.dump"
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

pg_restore -h "$HOST" -p "$PORT" -U "$DB_USER" -d "$DBNAME" --clean --if-exists "$INPUT_PATH"
echo "Restore complete from $INPUT_PATH"
