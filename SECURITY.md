# Security Scanning Policy

## Reporting Vulnerabilities
- Preferred contact: security@neogenesis.example (replace with the actual security mailbox).
- Include: affected version, reproduction steps, impact assessment, and any known mitigations.
- We target an initial response within 2 business days for confirmed reports.

## Authentication Security Guidance
- JWT clock skew: keep verifier clock skew tolerance low (recommended <= 60s) and synchronize hosts with NTP.
- Token lifetime: keep access tokens short-lived (`JWT_TTL_SECONDS`, default 3600s).
- Key rotation:
  - HMAC mode: rotate `JWT_SECRET` through staged deployment and dual-acceptance window.
  - OIDC mode: rotate keys via JWKS publisher and keep `JWT_JWKS_URL` highly available.
- Production mode forbids weak/default JWT configuration via startup readiness validation.

## Billing Security Guidance
- Stripe secrets must be provided via environment variables only.
- Webhooks must include `Stripe-Signature` and are verified with `STRIPE_WEBHOOK_SECRET`.
- Billing is disabled by default in dev/test; enable explicitly in production.

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

## Dependency Policy
- Every release must run dependency and artifact scanning in CI (`supply-chain.yml`, `release-readiness.yml`).
- Manual fallback (if CI scanning is unavailable):
  1. Build release artifact: `./gradlew clean shadowJar`
  2. Generate SBOM: SPDX JSON for `build/libs/*-all.jar`
  3. Scan with Grype and block release for fixable HIGH/CRITICAL vulnerabilities

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
