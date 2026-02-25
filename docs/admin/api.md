# Admin APIs (RBAC)

Feature-flagged admin endpoints for tenants, sites, users/roles, gateway inventory, and tenant feature flags.

## Enable
```
ADMIN_API_MODE=true
```

## RBAC
Roles are simplified to: `ADMIN`, `OPERATOR`, `VIEWER`.

- Read endpoints: `ADMIN` / `OPERATOR` / `VIEWER`
- Mutation endpoints: `ADMIN` only

All endpoints require:
- `Authorization: Bearer <JWT>`
- `X-Correlation-Id` header
- `tenant_id` query parameter

## Endpoints
Read:
- `GET /admin/roles?tenant_id=...`
- `GET /admin/users?tenant_id=...&limit=100`
- `GET /admin/tenants?tenant_id=...&limit=100`
- `GET /admin/sites?tenant_id=...&limit=100`
- `GET /admin/gateways?tenant_id=...&limit=100`
- `GET /admin/feature-flags?tenant_id=...`

Mutations (emit audit + evidence events):
- `POST /admin/tenants?tenant_id=...`
- `POST /admin/sites?tenant_id=...`
- `POST /admin/users?tenant_id=...`
- `PUT /admin/users/{userId}/role?tenant_id=...`
- `PUT /admin/feature-flags?tenant_id=...`

## Curl Examples

List gateways:
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/admin/gateways?tenant_id=tenant-1"
```

Create tenant:
```
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-1","name":"Tenant 1"}' \
  "http://localhost:8080/admin/tenants?tenant_id=tenant-1"
```

Create user:
```
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  -H "Content-Type: application/json" \
  -d '{"username":"operator-1","password":"change-me","role":"OPERATOR","tenantId":"tenant-1"}' \
  "http://localhost:8080/admin/users?tenant_id=tenant-1"
```

Set feature flag:
```
curl -X PUT -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-1","key":"export.audit.bundle","enabled":true}' \
  "http://localhost:8080/admin/feature-flags?tenant_id=tenant-1"
```
