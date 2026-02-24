# Admin Web (Scaffold)

The admin web console is feature-flagged and currently serves a placeholder UI
plus a minimal status API.

## Enable
```
ADMIN_WEB_MODE=true
```

## Endpoints
- `GET /admin/web` (HTML placeholder)
- `GET /admin/web/status` (JSON)

Both endpoints:
- Require `Authorization: Bearer <JWT>`
- Require `ADMIN` or `FOUNDER` role
- Require `X-Correlation-Id` (or `X-Request-Id`)

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  http://localhost:8080/admin/web/status
```
