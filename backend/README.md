# NeoGenesis Platform Server (Backend) v1.0.0

## Run Locally
1. Copy `.env.example` to `.env` and set values.
2. Start PostgreSQL + backend:
```bash
docker compose up --build
```
3. Or run directly:
```bash
./gradlew run
```

Server defaults:
- HTTP: `http://localhost:8080`
- Health: `GET /health`

## Required Environment Variables
Mandatory in production (`ENV=prod`):
- `JWT_SECRET`
- `DB_PASSWORD`

Core configuration:
- `ENV`, `HOST`, `PORT`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE`
- `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_REALM`, `JWT_SECRET`, `JWT_TTL_SECONDS`
- `CORS_ALLOWED_ORIGINS`
- `RATE_LIMIT_AUTH_PER_MINUTE`, `RATE_LIMIT_TELEMETRY_PER_MINUTE`, `RATE_LIMIT_TWIN_PER_MINUTE`
- `MAX_REQUEST_BODY_BYTES`, `REQUEST_TIMEOUT_MS`
- `ADMIN_BOOTSTRAP_ENABLED`, `ADMIN_BOOTSTRAP_USER`, `ADMIN_BOOTSTRAP_PASSWORD` or `ADMIN_BOOTSTRAP_PASSWORD_HASH`

Reference values are in `.env.example`.

## Auth Flow
1. Login:
```http
POST /auth/login
Content-Type: application/json

{"username":"admin","password":"admin-password"}
```
2. Receive JWT access token with claims:
- `sub` (user id)
- `username`
- `role`
- `tenantId` (optional)
- `iat`
- `exp`
3. Send token:
```http
Authorization: Bearer <token>
```

## Main Endpoints
- `POST /auth/login`
- `GET /health`
- `GET /ready`
- `GET /metrics`
- `POST /devices`
- `GET /devices`
- `POST /jobs`
- `GET /jobs`
- `GET /jobs/{jobId}`
- `POST /jobs/{jobId}/status`
- `POST /telemetry/job/{jobId}`
- `GET /telemetry/job/{jobId}?from=&to=&limit=`
- `POST /twin/job/{jobId}`
- `GET /audit/job/{jobId}`
- `GET /evidence/job/{jobId}`
- `POST /bioink/job/{jobId}/batch`
- `GET /bioink/job/{jobId}/batch`

## Docker
Build image:
```bash
docker build -t neogenesis-core-server:1.0.0 .
```

Run image:
```bash
docker run --rm -p 8080:8080 --env-file .env neogenesis-core-server:1.0.0
```

## Fat Jar
Build deployable artifact:
```bash
./gradlew clean shadowJar
```
Output:
- `build/libs/*-all.jar`

Run:
```bash
java -jar build/libs/<artifact>-all.jar
```
