# Runbook: Evidence Export Failure

## Symptoms
- `/audit/evidence` or `/audit/exports` endpoints return 5xx
- Evidence bundle generation fails

## Immediate Actions
1. Check audit log repository and DB connectivity.
2. Verify billing entitlement `audit:evidence_export` if enabled.
3. Inspect server logs for serialization or signature errors.

## Recovery
- Re-run export after resolving DB or entitlement issues.
- If signature key is missing, set `EVIDENCE_SIGNING_KEY_B64` and restart.

## Validation
- Evidence export endpoints return 200
- CSV/JSON bundles include expected entries
