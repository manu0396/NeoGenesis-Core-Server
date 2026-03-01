Traceability & Audit

Traceability
- Traceability matrix is stored in `src/main/resources/iso13485/traceability.csv`.
- Evidence map is stored in `src/main/resources/iso13485/requirement_test_evidence.csv`.
- Build gate: `traceabilityGate` task (runs after tests).

Audit Chain
- Audit events are hash-chained in storage.
- Use `GET /compliance/audit/verify-chain` for integrity checks.

Exports
- Evidence exports are available via `/exports/job/{jobId}.json` and `.csv`.
- Bundle hash is included for tamper evidence.
