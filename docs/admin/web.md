# Admin Web (Scaffold)

The admin web console is feature-flagged and currently serves a placeholder UI
plus a minimal status API.

## Enable
```
ADMIN_WEB_MODE=true
```

Optional OIDC login config:
```
ADMIN_WEB_OIDC_AUTH_URL=https://issuer.example/authorize
ADMIN_WEB_OIDC_CLIENT_ID=admin-web
ADMIN_WEB_OIDC_REDIRECT_URI=https://admin.example/callback
ADMIN_WEB_OIDC_SCOPE="openid email profile"
```

## Endpoints
- `GET /admin/web` (HTML placeholder)
- `GET /admin/web/status` (JSON)
- `GET /admin/web/login/oidc` (redirect)
- `GET /admin/web/gateways?tenant_id=...` (inventory)
- `GET /admin/web/gateways/export?tenant_id=...` (CSV export)

Both endpoints:
- Require `Authorization: Bearer <JWT>`
- Require `ADMIN` or `FOUNDER` role
- Require `X-Correlation-Id` (or `X-Request-Id`)
- Require `tenant_id` query parameter

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  http://localhost:8080/admin/web/status
```
