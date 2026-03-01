# Demo Deliverables Generator (Windows)

This demo produces client-ready deliverables without requiring Docker. If Docker is installed, it will be used automatically for PostgreSQL; otherwise, the server runs with an embedded demo DB.

## Generate Deliverables
From repo root:
```
scripts\generate_deliverables.ps1
```

## Outputs
Artifacts are written to:
```
build/deliverables/<timestamp>/
```

Files:
- `Sample_Run_Report.csv`
- `Audit_Bundle.zip`
- `manifest.json` (sha256 per file)

## Notes
- Uses env vars from `.env.example`.
- Admin bootstrap + evidence export are enabled for the demo run.
