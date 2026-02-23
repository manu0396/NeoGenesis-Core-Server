Productization Plan - Server v1.0.0

Status: Draft (2026-02-23)

Scope
- Docker-first delivery pack
- Billing + licensing entitlements (Stripe default, pluggable provider)
- Observability pack (health, metrics, correlation)
- Customer delivery documentation

Discovery Summary
- Ktor server with JWT auth, structured logs, health + metrics already present.
- Dockerfile + docker-compose exist but lack healthcheck + billing envs.
- Flyway migrations present (V1-V8). DB is Postgres/H2 with Hikari.
- Existing endpoints in modules + presentation routes; no billing layer yet.
- Build uses Gradle + shadowJar, JDK 21 toolchain.

Risks / Gaps
- No billing data model, provider abstraction, or webhook processing.
- Entitlements not enforced for premium endpoints.
- Docker pack missing healthcheck and billing config.
- No productization docs for billing/observability/docker.

Tasks

Build/Packaging
- Add Docker HEALTHCHECK for /health/ready.
- Ensure Docker env defaults for PORT/APP_PORT and billing flags.
- Update docker-compose with backend healthcheck and billing envs.
- Add .env.example billing variables and ignore .env.

Architecture / API Contracts
- Define BillingProvider interface and Stripe/Fake implementations.
- Add BillingService with idempotent webhook processing and subscription updates.
- Define EntitlementResolver and enforce on premium endpoints.
- Add billing routes:
  - POST /billing/checkout-session
  - POST /billing/portal-session
  - GET /billing/status
  - POST /billing/webhook

Data Model / Migrations
- Flyway V9 migration for:
  - plans
  - subscriptions
  - billing_events

Security
- Stripe webhook signature verification (STRIPE_WEBHOOK_SECRET).
- Deny-by-default on premium endpoints (402 Payment Required).

Observability
- Keep /health/live and /health/ready stable.
- Ensure X-Trace-Id response header is present (already via CallId).
- Ensure /metrics remains accessible (existing).

Testing
- Unit: EntitlementResolver.
- Unit: Stripe webhook signature verification.
- Endpoint tests using FakeBillingProvider (no network).

Docs
- docs/DEPLOYING_DOCKER.md
- docs/BILLING.md
- docs/OBSERVABILITY.md
- Update docs/RELEASING.md and CHANGELOG.md

Proposed Commit Sequence
1. docs: add productization plan + docker deployment guide
2. docker: add healthcheck + compose updates + .env.example changes
3. billing: add migration + persistence models
4. billing: provider interface + fake + stripe implementation
5. billing: service + webhook processing + entitlements enforcement
6. api: billing routes + tests
7. docs: billing/observability/releasing/changelog updates
