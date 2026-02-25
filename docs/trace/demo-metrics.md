# Demo Metrics (Trace)

These endpoints provide a minimal reproducibility score and drift alerts for demo runs.

## Score
```
GET /metrics/score?tenant_id=tenant-1&run_id=sim-run-1
```

Optional params:
- `baseline_n` (default 5)
- `metric_key` (default `pressure_kpa`)
- `z_threshold` (default 3.0)

## Alerts
```
GET /metrics/alerts?tenant_id=tenant-1&run_id=sim-run-1
```

## Curl Examples
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: demo-1" \
  "http://localhost:8080/metrics/score?tenant_id=tenant-1&run_id=sim-run-1"
```

```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: demo-2" \
  "http://localhost:8080/metrics/alerts?tenant_id=tenant-1&run_id=sim-run-1"
```
