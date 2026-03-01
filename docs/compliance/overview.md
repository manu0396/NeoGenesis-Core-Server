# Compliance Pack Overview

The Compliance Pack introduces dual‑control approvals for publishing protocol versions, retention policy configuration (WORM placeholder), and hooks for e‑sign and SCIM/SAML integration. All capabilities are gated by `COMPLIANCE_MODE`.

## Enable
```
COMPLIANCE_MODE=true
COMPLIANCE_WORM_MODE=true
COMPLIANCE_RETENTION_DAYS=3650
COMPLIANCE_ESIGN_ENABLED=false
COMPLIANCE_SCIM_ENABLED=false
COMPLIANCE_SAML_ENABLED=false
```

## Dual Control Workflow (Protocol Publish)
1. Request approval (admin):
```
POST /admin/compliance/protocols/{protocolId}/publish-approvals?tenant_id=...
Body: { "reason": "release approval" }
```
2. Approve (second admin):
```
POST /admin/compliance/publish-approvals/{approvalId}/approve?tenant_id=...
Body: { "comment": "reviewed" }
```
3. Publish version (existing gRPC publish). The publisher must be different from the approver.

## Retention Policy (Placeholder)
Configured via env:
- `COMPLIANCE_RETENTION_DAYS`
- `COMPLIANCE_WORM_MODE`

No active enforcement in v1; configuration is recorded for later enforcement.

## E‑sign + SCIM/SAML Hooks
When enabled, placeholder hooks log audit entries:
- `COMPLIANCE_ESIGN_ENABLED`
- `COMPLIANCE_SCIM_ENABLED`
- `COMPLIANCE_SAML_ENABLED`
