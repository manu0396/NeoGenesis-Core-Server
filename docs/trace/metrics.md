# Trace & Quality Metrics v1

Metrics API provides baseline definitions, drift rules (EWMA/CUSUM), alert storage, and run/weekly reports.
It is implemented in the `metrics-service` to keep Core lightweight.

## Enable
```
METRICS_SERVICE_MODE=true
METRICS_SERVICE_PORT=9090
METRICS_SERVICE_STORE_DIR=metrics-service-data
```

## Data Flow (Sample)
1. Define a baseline for a protocol/instrument metric.
2. Create drift rules (EWMA or CUSUM) for the same metric.
3. Ingest samples from gateways or control systems.
4. Alerts are generated on ingest and stored.
5. Fetch alerts or generate run/weekly reports.

## Baselines
`POST /metrics/baselines`
```json
{
  "protocolId": "proto-1",
  "instrumentId": "printer-a",
  "metricKey": "pressure_kpa",
  "mean": 90.0,
  "stddev": 1.5,
  "windowSize": 100
}
```

## Drift Rules
`POST /metrics/rules`
```json
{
  "protocolId": "proto-1",
  "instrumentId": "printer-a",
  "metricKey": "pressure_kpa",
  "type": "EWMA",
  "ewmaAlpha": 0.3,
  "ewmaZThreshold": 3.0,
  "severity": "warn",
  "enabled": true
}
```

`POST /metrics/rules`
```json
{
  "protocolId": "proto-1",
  "instrumentId": "printer-a",
  "metricKey": "pressure_kpa",
  "type": "CUSUM",
  "cusumK": 0.75,
  "cusumH": 7.5,
  "severity": "critical",
  "enabled": true
}
```

## Ingest Samples
`POST /metrics/ingest`
```json
{
  "metricType": "pressure",
  "metricKey": "pressure_kpa",
  "value": 95.2,
  "protocolId": "proto-1",
  "instrumentId": "printer-a",
  "runId": "run-1"
}
```

Required headers:
- `X-Tenant-Id`
- `X-Actor-Id`
- `X-Correlation-Id`

## Fetch Alerts
`GET /metrics/alerts?runId=run-1&metricKey=pressure_kpa`

## Reports
Run summary:
`GET /metrics/reports/run/{runId}`

Weekly summary:
`GET /metrics/reports/weekly?weekStart=2026-02-24`
