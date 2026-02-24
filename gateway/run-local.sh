#!/usr/bin/env bash
set -euo pipefail

export $(grep -v '^#' gateway/config/gateway.env.example | xargs)
./gradlew :gateway:run
