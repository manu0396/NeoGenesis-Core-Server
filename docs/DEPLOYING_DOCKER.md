Docker Deployment Guide

Prerequisites
- Docker + Docker Compose v2
- No external network calls are required for tests

Quick Start (Local)
1. Copy env template and edit:
   - `.env.example` -> `.env`
2. Build and start:
   - `docker compose up --build`
3. Verify:
   - `curl http://localhost:8080/health/live`
   - `curl http://localhost:8080/health/ready`

Ports
- HTTP: `8080`
- gRPC: `50051`

Healthchecks
- Container healthcheck uses `/health/ready`.
- Compose waits for Postgres health before starting server.

Billing Notes
- By default `BILLING_ENABLED=false` and the Fake provider is used.
- To enable Stripe:
  - Set `BILLING_ENABLED=true`
  - Set `BILLING_PROVIDER=stripe`
  - Provide `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID_PRO`
  - Provide `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_PORTAL_RETURN_URL`

Smoke Tests
- `curl -s http://localhost:8080/health/live`
- `curl -s http://localhost:8080/health/ready`
- `curl -s http://localhost:8080/metrics`

Troubleshooting
- If the server fails readiness, check DB connectivity and migrations.
- Ensure ports `8080` and `5432` are free.

Note on Docker Services
- Paid Docker services are not configured in this repo.
- Update this guide when Docker subscription/services are available.
