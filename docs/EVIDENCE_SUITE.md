Evidence Suite

Endpoints
- `GET /evidence/job/{jobId}` (JSON)
- `GET /exports/job/{jobId}.json`
- `GET /exports/job/{jobId}.csv`

Contents
- Job metadata
- Audit chain verification
- Telemetry + digital twin payload summaries
- Bundle hash
- Optional bundle signature (Ed25519)

Entitlement
- Requires `audit:evidence_export` when billing is enabled.

Signing (Optional)
- Set `EVIDENCE_SIGNING_KEY_B64` (PKCS8 Ed25519 private key, base64).
- Response includes `bundleSignature` and `signatureAlgorithm`.
