# CAPA Digital Procedure

## Workflow
1. Create CAPA record:
   - `POST /regulatory/capa`
2. Assign owner and requirement linkage.
3. Track status:
   - `POST /regulatory/capa/{capaId}/status`
4. Validate effectiveness with regression evidence.
5. Close CAPA with audit trail.

## Status model
- `OPEN`
- `IN_PROGRESS`
- `CLOSED`

## Auditability
- Every mutation creates immutable audit events.
