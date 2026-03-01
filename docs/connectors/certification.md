# Connector Certification Suite

The certification harness runs a simulated driver, ships latency metrics (mean/p95),
measures drop rate, and validates resilience via reconnect latency/success. It exports
both JSON and Markdown reports that you can attach to deliveries or upload.

## Run the harness
```bash
./gradlew :gateway:certifyConnector
```

Optional args:
```bash
./gradlew :gateway:certifyConnector \
  -PconnectorCertArgs="--driver=example-driver --events=500 --dropRate=0.01 --reconnectAt=250 --output=build/reports/connector-certification"
```

What changes between runs:
- `--driver` picks the connector/driver to exercise (`example-driver`, `simulated-connector`, etc.).
- `--events`, `--dropRate`, and `--reconnectAt` tune the latency/drop/reconnect load.
- `--output` controls where the JSON/MD reports land.

Output:
- `build/reports/connector-certification/certification-report.json`
- `build/reports/connector-certification/certification-report.md`

## What the reports contain
- `connectorId`, `driverId`, `eventsExpected`, `eventsReceived`, and `dropRate`.
- `meanLatencyMs`, `p95LatencyMs`, `reconnectLatencyMs`, `reconnectAttempts`, `reconnectSuccess`.
- `status` summarizes `pass`/`warn`/`fail` based on resilience thresholds.

## Admin endpoint (server)
Enable the feature flag so the server can record the certification request:
```
CONNECTOR_CERTIFICATION_MODE=true
```

Request:
```
POST /admin/connectors/certify
Headers: X-Correlation-Id
Body:
{
  "tenantId": "tenant-1",
  "connectorId": "my-driver",
  "connectorVersion": "1.0.0"
}
```

Response (example):
```
{
  "status": "accepted",
  "connectorId": "my-driver",
  "correlationId": "..."
}
```

Notes:
- Admin/Founder role required.
- An audit event is recorded for compliance.

## Optional submission to server
If you want the harness to POST to the server endpoint, set:
```
CERTIFICATION_SERVER_URL=http://localhost:8080/admin/connectors/certify
CERTIFICATION_TENANT_ID=tenant-1
CERTIFICATION_CORRELATION_ID=<optional>
CERTIFICATION_CONNECTOR_VERSION=1.0.0
CERTIFICATION_AUTH_TOKEN=<jwt>
```
