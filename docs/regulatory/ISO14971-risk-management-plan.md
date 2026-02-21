# ISO 14971 Risk Management Plan

## Scope
- SaMD server components handling telemetry, clinical data, and closed-loop control commands.

## Risk process
1. Hazard identification in `risk_register`.
2. Initial risk scoring (`severity * probability * detectability`).
3. Risk control definition and implementation.
4. Residual risk acceptance and monitoring.
5. Post-market surveillance with CAPA linkage.

## Evidence
- API: `/regulatory/risk`
- Audit: `/compliance/audit`
- CAPA linkage: `/regulatory/capa`
