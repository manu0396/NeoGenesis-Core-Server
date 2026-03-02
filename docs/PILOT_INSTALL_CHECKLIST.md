# Pilot Install Checklist (NeoGenesis Core Server)

1. Confirm `backend/VERSION` is `1.0.0` and `CHANGELOG.md` includes Phase 4 notes.
2. Configure env vars in `application.conf` or env overrides:
   - JWT secrets, DB URL, TLS/mTLS, observability endpoints.
3. Validate `device-policy.yaml` matches deployment policy.
4. Start services:
   - `docker-compose up --build`
5. Health probes:
   - `GET /health`, `GET /health/ready`
6. Metrics:
   - Prometheus endpoint at `/metrics` (or configured path).
7. Verify device-tier enforcement:
   - Tier2/Tier3 cannot control/edit/admin endpoints.
8. Verify evidence exports:
   - `/evidence-pack/job/{jobId}/report.csv`
9. Confirm gRPC endpoints allow Tier1 only.
10. Run tests:
    - `./gradlew.bat test`

