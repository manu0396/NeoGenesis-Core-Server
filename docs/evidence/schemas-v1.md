# Evidence Pack Schemas v1

This document defines the deterministic structure and versioned schemas for NeoGenesis Evidence Packs.

## Bundle Structure (ZIP)

The evidence bundle is a deterministic ZIP file containing:

- `{job_id}-report.csv`: Main clinical run report in CSV format.
- `{job_id}-report.pdf`: Human-readable version of the report.
- `event-chain.json`: (Optional) Cryptographic hash-chain of all protocol events.
- `manifest.json`: List of all files in the bundle with their SHA-256 hashes.
- `manifest.sig`: HMAC-SHA256 signature of `manifest.json`.

## CSV Schema (Report v1)

The CSV report follows a strict versioned schema to ensure portability across clinical systems.

| Column | Type | Description |
|--------|------|-------------|
| timestamp | ISO8601 | Event creation time in UTC. |
| category | String | Type of evidence (telemetry, twin, audit). |
| entity_id | UUID/String | Primary identifier of the resource. |
| data_json | JSON | Payload of the evidence entry. |
| sha256 | String | Hash of the row data for integrity verification. |

## Verification Procedure

1. Extract the ZIP bundle.
2. Read `manifest.sig` and verify it against `manifest.json` using the Enterprise Evidence Key.
3. For each entry in `manifest.json`, verify the SHA-256 hash of the corresponding file.
4. (Optional) Traverse `event-chain.json` to verify the temporal integrity of the run.
