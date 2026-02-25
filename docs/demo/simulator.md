# Digital Twin Simulator (Demo)

The simulator generates realistic telemetry with injected failure scenarios and writes events/telemetry to the RegenOps store.

## Endpoints

Start a simulated run:
```
POST /demo/simulator/runs?tenant_id=tenant-1
Headers:
  Authorization: Bearer <JWT>
  X-Correlation-Id: corr-1
Body:
{
  "protocolId": "sim-protocol",
  "runId": "sim-run-1",
  "samples": 120,
  "intervalMs": 1000,
  "failureAt": 90
}
```

Stream events (paged):
```
GET /demo/simulator/runs/{runId}/events?tenant_id=tenant-1&limit=250
```

Stream telemetry (paged):
```
GET /demo/simulator/runs/{runId}/telemetry?tenant_id=tenant-1&limit=250
```

## Notes
- Simulator is disabled when `COMPLIANCE_MODE=true` (publishing requires dual control).
- Requires role `ADMIN` or `OPERATOR` to start, and `ADMIN`/`OPERATOR`/`AUDITOR` to read streams.

## Demo End-to-End (Local)
1. Ensure `COMPLIANCE_MODE=false`.
2. Start server:
```
./gradlew run
```
3. Acquire JWT and run:
```
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  -H "Content-Type: application/json" \
  -d '{"protocolId":"sim-protocol","runId":"sim-run-1","samples":120,"intervalMs":1000,"failureAt":90}' \
  "http://localhost:8080/demo/simulator/runs?tenant_id=tenant-1"
```
4. Fetch events/telemetry:
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/demo/simulator/runs/sim-run-1/events?tenant_id=tenant-1"
```

```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/demo/simulator/runs/sim-run-1/telemetry?tenant_id=tenant-1"
```
