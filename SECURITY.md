# Security Scanning Policy

## Scope
- Runtime artifact scan: Shadow JAR generated in `build/libs/*-all.jar`.
- SBOM generation: SPDX JSON (`sbom.spdx.json`).
- Vulnerability scanner: Grype (`anchore/scan-action`).

## CI Policy
- Pull requests:
  - Always publish SARIF report to GitHub Code Scanning.
  - Fail only for `CRITICAL` vulnerabilities with a fix available.
- `main`/`master`:
  - Always publish SARIF report to GitHub Code Scanning.
  - Fail for `HIGH` and `CRITICAL` vulnerabilities with a fix available.
- `HIGH`/`CRITICAL` vulnerabilities without fix available:
  - Do not block by default.
  - Must be tracked automatically (issue) and mitigated/documented.

## Exception Rules
- Suppressions are managed in `.grype.yaml` and must be temporary.
- Every suppression must be reviewed and carry a clear expiration date.

## Active Suppressions
- None.

## Suppression Record Template
- `CVE/GHSA`:
- `Motivo`:
- `Alcance`:
- `Fecha de alta (UTC)`:
- `Owner`:
- `Fecha limite de revision (UTC)`:
- `Mitigacion compensatoria`:
