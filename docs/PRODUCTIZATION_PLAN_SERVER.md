Productization Plan - NeoGenesis Core Server (Suite)

Date: 2026-02-23
Owner: Release Captain

Current Gaps
- Billing + entitlements implemented, but evidence exports and supply-chain docs still missing.
- Observability is present; needs suite documentation alignment.
- Docker pack exists, but must note that paid Docker services are not configured yet.
- Integration readiness needs explicit adapter interfaces and documentation.

Tasks by Pillar

A) DeployKit
- Keep Dockerfile + docker-compose.
- Add comments in docker docs for “paid Docker services not configured yet.”
- Ensure healthcheck uses `/health/ready`.
- Ensure `.env.example` includes billing + evidence signing flags.

B) Observability Pack
- Confirm `/health/live`, `/health/ready`, `/metrics`.
- Confirm `X-Trace-Id` and correlation headers in responses.
- Document signals and alert baselines.

C) Billing/Licensing + Entitlements
- Verify Stripe webhook verification and idempotency.
- Ensure FakeBillingProvider for dev/test (no external network).
- Enforce entitlements on premium endpoints.

D) Evidence Suite Expansion
- Add export endpoints (JSON + CSV).
- Add optional bundle signature (Ed25519) using env key.
- Add SBOM generation docs and tasks.
- Add evidence suite docs.

E) Integration Readiness Scaffolding
- Add adapter interfaces and event contracts.
- Document boundaries for HL7/DICOM future services.

Commit Sequence (Proposed)
1. docs: productization plan + docker paid-service disclaimer
2. evidence: export endpoints + optional signature
3. supply-chain: SBOM task + docs
4. integration: adapter interfaces + docs
5. docs: evidence + traceability + releasing updates

Verification Commands
- `./gradlew clean build`
- `./gradlew test`
- `./gradlew run`
- `docker compose up --build`
- `curl /health/live` and `curl /health/ready`
- `GET /billing/status` with `BILLING_ENABLED=false`
- `GET /exports/job/{jobId}.json` and `.csv` (ensure entitlement enforcement)
