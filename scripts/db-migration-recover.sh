#!/usr/bin/env bash
set -euo pipefail

echo "Attempting Flyway repair + migrate..."
./gradlew flywayRepair
./gradlew flywayMigrate
echo "Migration recovery complete."
