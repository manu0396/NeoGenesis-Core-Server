# Deploying NeoGenesis Core Server

## Summary
Primary production artifact: **Shadow JAR** (`build/libs/*-all.jar`). Container builds are supported via `Dockerfile` or Jib.

## Runtime Requirements
- JDK 21 (server runtime)
- PostgreSQL 13+ (production)
- gRPC port: `NEOGENESIS_GRPC_PORT` (default `50051`)
- HTTP port: `PORT` / `NEOGENESIS_HTTP_PORT` (default `8080`)

## Environment Variables (Required for Production)
- `ENV=production`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_MODE=hmac` and `JWT_SECRET` (>= 32 chars), or `JWT_MODE=oidc` and `JWT_JWKS_URL`
- `NEOGENESIS_ENCRYPTION_ENABLED=true`
- `NEOGENESIS_PHI_KEY_B64`, `NEOGENESIS_PII_KEY_B64` (base64 secrets)
- `NEOGENESIS_MTLS_GRPC_ENABLED=true` + `NEOGENESIS_MTLS_GRPC_CERT_CHAIN_PATH` + `NEOGENESIS_MTLS_GRPC_PRIVATE_KEY_PATH`
- `NEOGENESIS_MTLS_GRPC_TRUST_CERT_PATH` if `NEOGENESIS_MTLS_GRPC_REQUIRE_CLIENT_AUTH=true`
- Billing (optional):
  - `BILLING_ENABLED=true`
  - `BILLING_PROVIDER=stripe`
  - `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `STRIPE_PRICE_ID_PRO`
  - `STRIPE_SUCCESS_URL`, `STRIPE_CANCEL_URL`, `STRIPE_PORTAL_RETURN_URL`

## TLS Termination Model
- HTTP: terminate TLS at ingress/reverse proxy (recommended). The app expects plain HTTP behind the proxy.
- gRPC: terminate with **direct mTLS** at the gRPC server (configured via `neogenesis.security.mtls.grpc.*`).

## Health Checks
- Liveness: `GET /health/live`
- Readiness: `GET /health/ready`
- Metrics (Prometheus): `GET ${NEOGENESIS_METRICS_PATH:-/metrics}`

## Database Migrations
- Flyway migrations run at startup when `neogenesis.database.migrateOnStartup=true`.
- For production, ensure migrations are executed as part of deployment or boot (recommended: boot).

## Running (Shadow JAR)
```bash
export ENV=production
export DB_URL=jdbc:postgresql://db:5432/neogenesis
export DB_USER=neogenesis
export DB_PASSWORD=****
export JWT_MODE=hmac
export JWT_SECRET=****
export NEOGENESIS_ENCRYPTION_ENABLED=true
export NEOGENESIS_PHI_KEY_B64=****
export NEOGENESIS_PII_KEY_B64=****
export NEOGENESIS_MTLS_GRPC_ENABLED=true
export NEOGENESIS_MTLS_GRPC_CERT_CHAIN_PATH=/secrets/grpc/tls.crt
export NEOGENESIS_MTLS_GRPC_PRIVATE_KEY_PATH=/secrets/grpc/tls.key
export EVIDENCE_SIGNING_KEY_B64=****
java -jar build/libs/neogenesis-core-server-all.jar
```

## Docker (Optional)
```bash
docker build -t neogenesis-core-server:1.0.0 .
docker run --rm -p 8080:8080 -p 50051:50051 --env-file .env neogenesis-core-server:1.0.0
```

## Rollback
1. Re-deploy the previous `*-all.jar` or container tag.
2. Ensure DB migrations are backward-compatible before applying schema changes.
