#!/usr/bin/env bash
set -euo pipefail

SERVER_CERT_PATH="${SERVER_CERT_PATH:-/etc/neogenesis/server.crt}"
SERVER_KEY_PATH="${SERVER_KEY_PATH:-/etc/neogenesis/server.key}"
CLIENT_CERT_PATH="${CLIENT_CERT_PATH:-/etc/neogenesis/gateway/client.crt}"
CLIENT_KEY_PATH="${CLIENT_KEY_PATH:-/etc/neogenesis/gateway/client.key}"

echo "Rotate certs by replacing the files at:"
echo "  Server cert: $SERVER_CERT_PATH"
echo "  Server key : $SERVER_KEY_PATH"
echo "  Client cert: $CLIENT_CERT_PATH"
echo "  Client key : $CLIENT_KEY_PATH"

echo "Restart services after replacement."
echo "  sudo systemctl restart neogenesis-core-server"
echo "  sudo systemctl restart neogenesis-gateway"
