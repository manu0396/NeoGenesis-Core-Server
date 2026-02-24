# Runbook: DB Migration Failure

## Symptoms
- `/health/ready` failing
- Startup logs show Flyway validation or migration errors

## Immediate Actions
1. Stop rollout or scale down new pods.
2. Capture logs and the failing migration version.
3. Verify DB connection settings and secrets.

## Recovery
- If a migration was partially applied:
  - Inspect `flyway_schema_history` for the failed version.
  - Fix migration and re-deploy.
- If rollback is required:
  - Follow `deploy/runbooks/rollback.md`.

## Validation
- `/health/ready` returns 200
- Flyway logs show all migrations applied
