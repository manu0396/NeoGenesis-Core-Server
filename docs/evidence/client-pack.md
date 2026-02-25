# Evidence Client Pack

Use these exports as a stable, client-ready evidence pack for audits and customer delivery.

## Recommended Package
- `report.csv` (schema versioned)
- `report.pdf` (human‑readable)
- `bundle.zip` (includes manifest + optional event hash‑chain)

## What To Send
1. `bundle.zip` (preferred single artifact)
2. If a single file is needed, send `report.pdf` plus `report.csv`.

## File Names (Deterministic)
- `report.csv`
- `report.pdf`
- `bundle.zip`
- `manifest.json` (inside bundle)
- `event-chain.json` (optional, inside bundle)

## Where to Find Outputs
- `GET /evidence-pack/job/{jobId}/report.csv?tenant_id=...`
- `GET /evidence-pack/job/{jobId}/report.pdf?tenant_id=...`
- `GET /evidence-pack/job/{jobId}/bundle.zip?tenant_id=...`

## Notes
- CSV includes `schema_version` to keep clients stable across updates.
- Bundle `manifest.json` provides sha256 per file for integrity checks.
