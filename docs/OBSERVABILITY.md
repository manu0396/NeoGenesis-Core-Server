Observability

Health
- `GET /health/live` - liveness
- `GET /health/ready` - readiness (DB + migrations + traceability)
- `GET /health` - basic info

Metrics
- `GET /metrics` (Prometheus format)

Tracing / OTEL
- Set `OTEL_EXPORTER_OTLP_ENDPOINT` to your collector (e.g. `http://otel-collector:4317`).
- Set `OTEL_SERVICE_NAME` (e.g. `neogenesis-core-server`).
- Optional: `OTEL_RESOURCE_ATTRIBUTES=service.version=1.0.1,env=local`.

Logging
- Server supports JSON logs when `LOG_FORMAT=json`.
- Gateway logs are stdout; use systemd journald or a log shipper.
- JSON logs include `correlationId` and `tenantId` when available.

Correlation
- Incoming requests accept `X-Correlation-Id` or `X-Request-Id`.
- Responses echo the correlation id in headers.
- Logs include `traceId`, `endpoint`, `status`, and `durationMs`.

Recommended Alerts
- Readiness failures on `/health/ready`
- 5xx error rate spikes
- Increased request duration / timeout rate
- DB connection pool exhaustion
