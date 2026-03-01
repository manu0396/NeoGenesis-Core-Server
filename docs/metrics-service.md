# Metrics Service (Trace & Quality)

Standalone service skeleton for ingesting metrics and providing quality/score APIs.

## Enable & Run
```
METRICS_SERVICE_MODE=true
METRICS_SERVICE_PORT=9090
```

Start:
```
./gradlew :metrics-service:run
```

## Endpoints
- `GET /health`
- `GET /metrics/score`
- `GET /metrics/alerts`
- `POST /metrics/ingest`

All `/metrics/*` endpoints require headers:
- `X-Tenant-Id`
- `X-Actor-Id`
- `X-Correlation-Id` (or `X-Request-Id`)

## Example
```
curl -H "X-Tenant-Id: tenant-a" \
  -H "X-Actor-Id: actor-1" \
  -H "X-Correlation-Id: corr-1" \
  http://localhost:9090/metrics/score
```
