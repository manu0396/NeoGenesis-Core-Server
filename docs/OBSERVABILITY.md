Observability

Health
- `GET /health/live` - liveness
- `GET /health/ready` - readiness (DB + migrations + traceability)
- `GET /health` - basic info

Metrics
- `GET /metrics` (Prometheus format)

Correlation
- Incoming requests accept `X-Correlation-Id` or `X-Request-Id`.
- Responses echo the correlation id in headers.
- Logs include `traceId`, `endpoint`, `status`, and `durationMs`.

Recommended Alerts
- Readiness failures on `/health/ready`
- 5xx error rate spikes
- Increased request duration / timeout rate
- DB connection pool exhaustion
