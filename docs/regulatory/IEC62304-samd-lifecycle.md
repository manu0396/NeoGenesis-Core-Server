# IEC 62304 SaMD Lifecycle Package

## Lifecycle artifacts
- Software requirements: `src/main/resources/iso13485/traceability.csv`
- Architecture/design: `docs/neotec-backlog.md` and source modules.
- Verification evidence: `src/main/resources/iso13485/requirement_test_evidence.csv`
- Change control: immutable audit chain (`/compliance/audit/verify-chain`).
- Release gate: `traceabilityGate` Gradle task in CI.

## Safety classification
- Server functions affecting control loop are managed as safety-critical components.
- Releases blocked if requirement-to-evidence mapping is incomplete.
