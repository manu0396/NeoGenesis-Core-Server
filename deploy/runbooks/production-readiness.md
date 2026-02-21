# NeoGenesis Core Server 1.0.0 - Production Readiness Checklist

## Runtime Safety Gates
- `NEOGENESIS_RUNTIME_ENVIRONMENT=production` is set.
- Startup validation passes (no default JWT secret, no in-memory DB, encryption enabled, gRPC mTLS enabled).
- PHI/PII encryption keys are resolved at startup (fail-fast if missing).
- gRPC certificate/key/trust files are mounted and readable.

## Security Baseline
- JWT signing secret is sourced from Kubernetes Secret.
- PHI/PII encryption keys are sourced from Kubernetes Secret.
- HTTP API is reachable only through authenticated ingress.
- gRPC endpoint requires mTLS + JWT role claims.

## Data & Compliance
- Flyway migrations are applied successfully on startup.
- ISO 13485 traceability CSV files are present in the container image.
- Outbox provider is configured to cloud transport (EventBridge/SQS/PubSub), not logging mode.

## Observability
- `/health/live` and `/health/ready` endpoints respond from cluster probes.
- Prometheus can scrape metrics endpoint.
- Alertmanager route is active for page-level latency alerts.

## Release Verification
- Run `./gradlew clean check` before release.
- Container image tag is pinned to `1.0.0` in deployment manifests.
- Create secrets from `deploy/k8s/base/secrets.example.yaml` with real values before apply.
