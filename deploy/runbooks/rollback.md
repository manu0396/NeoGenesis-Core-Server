# Rollback Runbook

## Automatic rollback triggers
- Canary analysis failure (`p99 > 50ms`, DLQ growth, or circuit-breaker open events).
- Readiness probe failures for 3 consecutive checks.
- Manual SRE override during incident bridge.

## Procedure
1. Freeze traffic shift:
   - `kubectl argo rollouts pause neogenesis-core`
2. Abort rollout and restore stable:
   - `kubectl argo rollouts abort neogenesis-core`
3. Confirm blue service receives 100% traffic:
   - `kubectl argo rollouts get rollout neogenesis-core`
4. Validate health and latency:
   - `curl /health/ready`
   - Prometheus check `p99 < 50ms`.
5. Open CAPA and incident record:
   - `POST /regulatory/capa`
   - `GET /compliance/audit`.
