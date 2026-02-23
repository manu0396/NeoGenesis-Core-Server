# Changelog

## [1.0.0] - 2026-02-22
- Added production-oriented typed configuration (`AppConfig`) with env-first loading and prod fail-fast checks for `JWT_SECRET` and `DB_PASSWORD`.
- Added canonical JWT/RBAC model with roles: `ADMIN`, `OPERATOR`, `AUDITOR`, `INTEGRATION`.
- Added `/auth/login` with bcrypt password verification and in-memory brute force protection.
- Added hardened HTTP middleware: call ID (`X-Request-Id`), consistent error model, security headers, configurable CORS deny-by-default, timeout guard, and request-size limit.
- Added v1 schema migration with `roles`, `users`, `devices`, `print_jobs`, `telemetry_records`, `digital_twin_metrics`, and `audit_logs`.
- Added audit hash-chain implementation and `GET /audit/job/{jobId}` verification endpoint.
- Added evidence package endpoint `GET /evidence/job/{jobId}`.
- Added v1 modules: `AuthModule`, `HealthModule`, `TelemetryModule`, `AuditModule`, `DevicesModule`, `JobsModule`, `BioinkModule`.
- Added liveness/readiness/metrics endpoints: `/health`, `/ready`, `/metrics`.
- Switched logging output to JSON (logstash logback encoder) with request context fields in MDC.
- Added local deployment artifacts: `.env.example` and `docker-compose.yml`.
- Added integration tests covering auth, authorization, telemetry ingestion, and audit-chain verification.
