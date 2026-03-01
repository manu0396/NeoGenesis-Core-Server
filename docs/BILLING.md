Billing & Entitlements

Overview
- Stripe is the default billing provider.
- Billing is disabled by default (dev/test). When disabled, all entitlements are allowed.
- Billing data is stored in Postgres using Flyway V9.

Plans
- Free: `free`
- Pro: `pro`

Entitlements
- `audit:evidence_export`
- `compliance:traceability_audit`

Endpoints
- `POST /billing/checkout-session`
  - Body: `{ "planId": "pro", "customerEmail": "user@example.com" }`
  - Returns: `{ "url": "https://..." }`
- `POST /billing/portal-session`
  - Returns: `{ "url": "https://..." }`
- `GET /billing/status`
  - Returns: `{ planId, status, periodEnd, entitlements[] }`
- `POST /billing/webhook` (public)
  - Requires `Stripe-Signature` header

Webhook Processing
- Signature verification uses `STRIPE_WEBHOOK_SECRET`.
- Idempotent processing via `billing_events`.
- Supports:
  - `checkout.session.completed`
  - `customer.subscription.created`
  - `customer.subscription.updated`
  - `customer.subscription.deleted`

Configuration (Env)
- `BILLING_ENABLED` (true/false)
- `BILLING_PROVIDER` (stripe|fake)
- `BILLING_PLAN_FREE_ID`
- `BILLING_PLAN_PRO_ID`
- `BILLING_PLAN_FEATURES_FREE`
- `BILLING_PLAN_FEATURES_PRO`
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_PRICE_ID_FREE` (optional)
- `STRIPE_PRICE_ID_PRO`
- `STRIPE_SUCCESS_URL`
- `STRIPE_CANCEL_URL`
- `STRIPE_PORTAL_RETURN_URL`

Enforcement
- Evidence export endpoint requires `audit:evidence_export`.
- Traceability audit endpoint requires `compliance:traceability_audit`.
- When billing is disabled, entitlements are not enforced.
