# Evidence Exports

Evidence pack exports provide stable CSV, PDF, and tamper-evident bundles for a job.

## Enable
```
EVIDENCE_PACK_MODE=true
```

Optional event hash-chain in bundles:
```
EVIDENCE_EVENT_CHAIN_ENABLED=true
```

## Endpoints
- `GET /evidence-pack/job/{jobId}/report.csv?tenant_id=...`
- `GET /evidence-pack/job/{jobId}/report.pdf?tenant_id=...`
- `GET /evidence-pack/job/{jobId}/bundle.zip?tenant_id=...`

All endpoints require:
- `Authorization: Bearer <JWT>`
- Role `ADMIN` or `AUDITOR`
- `X-Correlation-Id` header
- `tenant_id` query parameter

## CSV Schema (stable)
Headers:
```
job_id,tenant_id,actor_id,correlation_id,generated_at,server_version,telemetry_count,twin_count,audit_event_count,last_telemetry_at,last_twin_at
```

## Bundle Contents
`bundle.zip` includes:
- `report.csv`
- `report.pdf`
- `manifest.json` (sha256 per file)
- `event-chain.json` (optional, when `EVIDENCE_EVENT_CHAIN_ENABLED=true`, hash chain over run events)

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/evidence-pack/job/job-1/report.csv?tenant_id=tenant-1"
```

## Validate Bundle Integrity
1. Extract the bundle:
```
unzip bundle.zip -d evidence-pack
```
2. Recompute sha256 for each file and compare to `manifest.json`.
```
Get-ChildItem evidence-pack | ForEach-Object {
  $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
  "$($_.Name): $hash"
}
```

`event-chain.json` can be validated by ensuring each entry's `prevHash` matches the previous `eventHash`.
