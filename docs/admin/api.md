# Admin APIs (RBAC)

Feature-flagged admin endpoints for tenants, sites, users, and roles.

## Enable
```
ADMIN_API_MODE=true
```

## Endpoints
- `GET /admin/roles?tenant_id=...`
- `GET /admin/users?tenant_id=...&limit=100`
- `GET /admin/tenants?tenant_id=...`
- `GET /admin/sites?tenant_id=...`

All endpoints require:
- `Authorization: Bearer <JWT>`
- Role `ADMIN` or `FOUNDER`
- `X-Correlation-Id` header
- `tenant_id` query parameter

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/admin/roles?tenant_id=tenant-1"
```
