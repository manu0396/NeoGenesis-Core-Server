# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-02-23
### Added
- Production readiness validation at startup (secrets, encryption, mTLS, outbox provider).
- Health endpoints `/health/live` and `/health/ready` aligned with CI checks.
- Correlation ID support via `X-Correlation-Id` and `X-Request-Id`.
- Configurable database timeouts and metrics path.
- OpenTelemetry HTTP tracing (optional) and Prometheus-compatible metrics output.
- Billing + entitlements foundation (plans, subscriptions, webhooks, Stripe provider).
- Docker deployment guide and billing/observability documentation.
- Evidence suite exports (JSON/CSV) with optional bundle signature.
- Supply-chain SBOM generation task (CycloneDX) and documentation.
- Integration readiness contracts and documentation.
- JDK 21 toolchain alignment for build and CI.
- Explicit Java and Kotlin toolchain pinning to JDK 21 with auto-download disabled.
- Canonical CI release path (`clean build`, `ktlintCheck`, `detekt`, `test`, `shadowJar`) plus artifact upload.
- Release docs updated with `javaToolchains` and `GRADLE_USER_HOME` reproducibility guidance.

### Changed
- Docker base image updated to JDK 21.
- Docker image now includes healthcheck for `/health/ready`.
- Metrics registry switched to Prometheus-backed registry.
- Removed Foojay toolchain resolver plugin dependency to avoid flaky temp JDK provisioning paths.

### Security
- Updated security reporting guidance and CI JDK alignment.
- Added JWT clock-skew/key-rotation guidance and dependency-policy fallback steps.
