# Commercial Pipeline (Internal)

This module is feature-flagged and **disabled by default**. Enable it only for internal fundraising/grants workflows.

## Enable

Set:

```
COMMERCIAL_MODE=true
```

Or in config:

```
neogenesis.commercial.mode = true
```

## Roles

Restricted to `ADMIN` or `FOUNDER` roles only.

## Required fields

All write operations require:
- `tenantId`
- `correlationId`

All list/export endpoints require:
- `tenant_id` query param
- `X-Correlation-Id` header

## Endpoints

- `POST /commercial/accounts`
- `POST /commercial/accounts/{id}`
- `GET /commercial/accounts?tenant_id=...`
- `POST /commercial/contacts`
- `GET /commercial/contacts?tenant_id=...&account_id=...`
- `POST /commercial/opportunities`
- `POST /commercial/opportunities/{id}`
- `GET /commercial/opportunities?tenant_id=...&stage=...`
- `POST /commercial/lois`
- `POST /commercial/lois/{id}/attachment`
- `GET /commercial/lois?tenant_id=...&opportunity_id=...`
- `GET /commercial/pipeline/summary?tenant_id=...`
- `GET /commercial/pipeline/export?tenant_id=...` (CSV)

## Evidence / Audit

All create/update operations emit:
- `audit_events` (immutable audit trail)
- `commercial_activity_log` (immutable activity log)

These can be exported alongside the standard evidence bundle for grants/investors.
