# Admin Web

Minimal admin console served as a standalone React app in `admin-web`.

## Enable Backend Admin Web APIs
```
ADMIN_WEB_MODE=true
```

Optional OIDC config for the backend redirect endpoint:
```
ADMIN_WEB_OIDC_AUTH_URL=https://issuer.example/authorize
ADMIN_WEB_OIDC_CLIENT_ID=admin-web
ADMIN_WEB_OIDC_REDIRECT_URI=https://admin.example/callback
ADMIN_WEB_OIDC_SCOPE="openid email profile"
```

## Admin Web UI (Vite)
From repo root (Windows PowerShell):
```
cd admin-web
npm install
npm run dev
```

### Windows dev notes
1. Open PowerShell, run `Set-ExecutionPolicy Bypass -Scope Process -Force` if scripts are blocked.
2. The Vite dev server defaults to `http://localhost:5173`; set `VITE_API_BASE_URL` in `.env` (or copy `.env.example`) when the backend runs on another host or emulator (`http://10.0.2.2:8080` for Android emulators).
3. Re-run `npm run dev` after editing `.env.local`.

Env overrides for the UI:
```
VITE_API_BASE_URL=http://localhost:8080
VITE_OIDC_AUTH_URL=https://issuer.example/authorize
VITE_OIDC_CLIENT_ID=admin-web
VITE_OIDC_REDIRECT_URI=https://admin.example/callback
VITE_OIDC_SCOPE="openid email profile"
```

## Login Options
- Dev login uses `POST /auth/login` with username/password.
- OIDC token login lets you paste an access token (JWT) and optionally override role and tenant.
- The UI passes `Authorization: Bearer <token>`, `tenant_id`, and `X-Correlation-Id` for admin requests.

## UI Views
- Gateways inventory (calls `/admin/web/gateways?tenant_id=...`).
- Tenants and sites list (attempts `/admin/tenants` and `/admin/sites`, falls back to local stub data).
- Feature flags toggle (admin-only, stored in local storage for now).

## Admin Web Endpoints
- `GET /admin/web` (HTML placeholder)
- `GET /admin/web/status` (JSON)
- `GET /admin/web/login/oidc` (redirect)
- `GET /admin/web/gateways?tenant_id=...` (inventory)
- `GET /admin/web/gateways/export?tenant_id=...` (CSV export)

All endpoints:
- Require `Authorization: Bearer <JWT>`
- Require `ADMIN` or `FOUNDER` role
- Require `X-Correlation-Id` (or `X-Request-Id`)
- Require `tenant_id` query parameter

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/admin/web/status?tenant_id=tenant-a"
```
