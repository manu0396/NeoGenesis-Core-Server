#!/usr/bin/env bash
set -euo pipefail

QUEUE_PATH="${QUEUE_PATH:-gateway-data/queue}"

echo "Queue path: $QUEUE_PATH"
if [[ ! -d "$QUEUE_PATH" ]]; then
  echo "Queue directory not found"
  exit 1
fi

echo "Queue files:"
ls -lah "$QUEUE_PATH"

echo "Restarting gateway to drain backlog..."
if command -v systemctl >/dev/null 2>&1; then
  sudo systemctl restart neogenesis-gateway
else
  echo "systemctl not available. Please restart gateway manually."
fi
