# Connector Certification Suite

The certification harness runs a simulated driver, measures latency and drop rate,
and emits a JSON + Markdown report.

## Run the harness
```bash
./gradlew :gateway:certifyConnector
```

Optional args:
```bash
./gradlew :gateway:certifyConnector \
  -PconnectorCertArgs="--driver=example-driver --events=500 --dropRate=0.01 --reconnectAt=250 --output=build/reports/connector-certification"
```

Output:
- `build/reports/connector-certification/certification-report.json`
- `build/reports/connector-certification/certification-report.md`

## Admin endpoint (server)
Enable feature flag:
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
- An audit event is recorded.

## Optional submission to server
If you want the harness to POST to the server endpoint, set:
```
CERTIFICATION_SERVER_URL=http://localhost:8080/admin/connectors/certify
CERTIFICATION_TENANT_ID=tenant-1
CERTIFICATION_CORRELATION_ID=<optional>
CERTIFICATION_CONNECTOR_VERSION=1.0.0
CERTIFICATION_AUTH_TOKEN=<jwt>
```
