#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE=docker-compose.local.yml

if [ "${1:-}" = "obs" ]; then
  docker compose -f $COMPOSE_FILE --profile observability up --build
else
  docker compose -f $COMPOSE_FILE up --build
fi
