# Audit Bundle (Tamper-Evident)

Exports a ZIP bundle containing telemetry, twin, audit CSVs plus a manifest
and hash chain to detect tampering.

## Enable
```
AUDIT_BUNDLE_MODE=true
```

## Endpoint
`GET /audit-bundle/job/{jobId}.zip?tenant_id=...`

Requirements:
- `Authorization: Bearer <JWT>`
- Role `ADMIN` or `AUDITOR`
- `X-Correlation-Id` header
- `tenant_id` query parameter

Bundle contents:
- `telemetry.csv`
- `twin.csv`
- `audit.csv`
- `manifest.json` (file hashes + bundle hash)
- `hash-chain.json` (file hash chain)

## Example
```
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: corr-1" \
  "http://localhost:8080/audit-bundle/job/job-1.zip?tenant_id=tenant-a" -o audit-bundle.zip
```
