# Evidence Client Pack

The server exposes stable, client-ready evidence exports that you can fetch via HTTPS and then hand off to customers or auditors.

## 1. CSV schema (stable v1)
Every CSV export (from `/evidence-pack/job/{jobId}/report.csv`) starts with this header:
```
schema_version,job_id,tenant_id,actor_id,correlation_id,generated_at,server_version,telemetry_count,twin_count,audit_event_count,last_telemetry_at,last_twin_at
```
The `schema_version` column is currently `1`, and its presence allows clients to validate the payload before unpacking. Subsequent schema changes will increment this version.

## 2. API endpoints
- `GET /evidence-pack/job/{jobId}/report.csv?tenant_id={tenantId}` — returns the CSV+metadata row described above.
- `GET /evidence-pack/job/{jobId}/bundle.zip?tenant_id={tenantId}` — returns a ZIP bundle containing:
  * `${sanitizedJobId}-report.csv`
  * `${sanitizedJobId}-report.pdf`
  * `manifest.json` (includes `sha256` + `size` for each file)
  * `event-chain.json` (when enabled via `EVENT_CHAIN_ENABLED`)

The `sanitizedJobId` is derived by replacing non-alphanumeric characters with `_`, so filenames are deterministic and safe for downloads.

## 3. Audit bundle (manifest + hash chain)
Exported audit bundles (via `GET /evidence-pack/job/{jobId}/bundle.zip`) include:
1. `manifest.json` — lists every file’s name + SHA-256 hash.
2. `hash-chain.json` — a chained proof over the telemetry/twin/audit CSVs.
3. Telemetry/twin/audit CSVs named `<sanitizedJobId>-telemetry.csv`, `<sanitizedJobId>-twin.csv`, etc.
4. `manifest.json` is also returned in the `AuditBundle` response object for easy verification.

## 4. Sample outputs (after download)
- `build/reports/evidence/<jobId>/report.csv`
- `build/reports/evidence/<jobId>/report.pdf`
- `build/reports/evidence/<jobId>/bundle.zip` → contains `manifest.json`, `hash-chain.json`, `event-chain.json`

Inside the ZIP you can unzip and inspect `manifest.json`. Each entry contains:
```
{
  "file": "demo-run-report.csv",
  "sha256": "..."
  "size": 3456
}
```

## 5. Verifying integrity
1. `curl -o bundle.zip "http://localhost:8080/evidence-pack/job/{jobId}/bundle.zip?tenant_id=tenant-1"`
2. Unzip (`tar -xf bundle.zip`), read `manifest.json`.
3. Compute SHA-256 for each file and compare with the manifest entries:
   ```powershell
   Get-FileHash demo-run-report.csv -Algorithm SHA256
   ```
4. The manifest’s `hashAlgorithm` field currently reads `SHA-256`.
5. `event-chain.json`, if present, lets you replay telemetry hashes to prove nothing changed.

## 6. Suggested workflow
1. Fetch CSV + bundle using the above endpoints.
2. Use `manifest.json` + `sha256` to ensure tamper evidence.
3. Provide the CSV and ZIP (with `manifest.json`) to your client; they do not need server access.
# End of doc
