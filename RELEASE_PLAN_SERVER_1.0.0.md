# Release Plan: NeoGenesis Core Server v1.0.0

## Findings / Risks
- Core runtime is already close to v1.0.0: version is `1.0.0`, health/readiness endpoints exist, structured error payloads are in place, and startup production-readiness validation is enforced.
- Quality tooling is already present in build (`ktlint` + `detekt`) and wired into `check`; CI still needs one canonical green-path workflow for release builds plus artifact upload.
- Primary deployment artifact can be standardized as Shadow JAR (`build/libs/*-all.jar`) with Jib TAR as optional secondary output.
- Toolchain stability risk remains:
  - `settings.gradle.kts` still applies `org.gradle.toolchains.foojay-resolver-convention`, which can trigger remote resolver behavior and flaky temp JDK state.
  - user-reported warning references invalid auto-provisioned temp JDK path.
- Current `.env.example` includes insecure developer defaults that are acceptable for dev but need clear separation and production-safe guidance in docs.
- Security/reporting docs exist but need final release-level completeness and consistency with CI dependency policy.

## Tasks

### Build / Reproducibility
- Remove unnecessary Foojay resolver plugin from `settings.gradle.kts`.
- Keep explicit JVM toolchain pin in Gradle and align Java + Kotlin toolchains to JDK 21.
- Keep Gradle installation lookup deterministic (`JAVA_HOME`/`JDK_HOME`, no auto-download).
- Verify `./gradlew javaToolchains`, `./gradlew clean build`, and `./gradlew test` are stable without temp-JDK warnings.

### Versioning
- Confirm `version = "1.0.0"` in Gradle build.
- Confirm runtime version consistency (`backend/VERSION`, logs, docs).

### API Contracts
- Validate error mapping consistency and structured error shape for common failures.
- Confirm request timeout + payload size protections are active.
- Confirm auth model is deny-by-default on protected business routes.

### Config / Secrets
- Ensure env-based configuration for dev/test/prod is documented with required production vars.
- Keep unsafe defaults blocked in production via `ProductionReadinessValidator`.
- Ensure `.env.example` is explicit about dev-only values and required production replacements.

### Security
- Finalize `SECURITY.md` to include vulnerability reporting flow, dependency policy, and exception handling.
- Ensure TLS termination model and JWT key management guidance are documented in deploy/release docs.

### Observability
- Confirm `/health/live`, `/health/ready`, correlation IDs, and metrics endpoint behavior.
- Keep JSON logging fields (`traceId`, `service`, `env`, `version`) consistent and documented.

### Reliability
- Confirm graceful shutdown behavior (gRPC server shutdown + DB pool close).
- Confirm DB pool/Flyway startup behavior and timeout settings are production-ready.

### Testing
- Keep deterministic unit/integration test execution in CI.
- Ensure traceability gate remains part of release checks.

### CI/CD
- Add/fix a primary CI workflow that runs:
  - `./gradlew clean build`
  - lint/static analysis (`ktlintCheck`, `detekt` via `check`)
  - tests
- Cache Gradle and upload release artifacts (`*-all.jar`, distributions, optional Jib TAR).

### Packaging / Deployment
- Primary production artifact: Shadow JAR.
- Keep Jib TAR optional for container supply-chain workflows.
- Ensure `docs/DEPLOYING.md` and `docs/RELEASING.md` include exact build/run/publish steps, health checks, migration behavior, rollback notes.

## Proposed Small-Commit Sequence
1. `build: stabilize toolchain resolution and remove foojay resolver dependency`
2. `ci: add canonical release CI workflow with clean build and artifact upload`
3. `docs: finalize releasing/deploying/security/changelog for v1.0.0`
4. `chore: verify full release command matrix and capture outcomes`
