# Evidence Pack v1 (Run Report)

Exports a lightweight CSV report for a job run with telemetry/twin/audit counts.

## Enable
```
EVIDENCE_PACK_MODE=true
```

## Endpoint
`GET /evidence-pack/job/{jobId}/report.csv?tenant_id=...`

Requirements:
- `Authorization: Bearer <JWT>`
- Role `ADMIN` or `AUDITOR`
- `X-Correlation-Id` header
- `tenant_id` query parameter

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/evidence-pack/job/job-1/report.csv?tenant_id=tenant-a"
```
