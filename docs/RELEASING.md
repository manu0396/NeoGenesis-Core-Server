# Releasing NeoGenesis Core Server

## Prerequisites
- JDK 21
- Gradle wrapper `./gradlew`
- Clean working tree
- Writable Gradle home (`GRADLE_USER_HOME`)

Recommended local setup:
```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="${PWD}/.gradle-user"
```

Verify toolchain resolution before release:
```bash
./gradlew --version
./gradlew javaToolchains
```
Expected: JDK 21 is detected, and toolchain auto-detection/auto-download are disabled.

## Versioning
- Release tag format: `v1.0.0`
- Gradle version: `build.gradle.kts` `version = "1.0.0"`
- Runtime version: `APP_VERSION` env var or `backend/VERSION`

## Build & Test
```bash
./gradlew clean build
./gradlew test
```

## Release Artifacts
Primary artifact: Shadow JAR.
```bash
./gradlew shadowJar
ls -la build/libs/*-all.jar
```

Optional container artifact:
```bash
./gradlew jibBuildTar
```

Docker image (build locally):
```bash
docker build -t neogenesis-core-server:1.0.0 .
```

SBOM (CycloneDX):
```bash
./gradlew cyclonedxBom
```

## Billing Env (if enabled)
Required when `BILLING_ENABLED=true` and `BILLING_PROVIDER=stripe`:
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_PRICE_ID_PRO`
- `STRIPE_SUCCESS_URL`
- `STRIPE_CANCEL_URL`
- `STRIPE_PORTAL_RETURN_URL`

## Tagging
```bash
git tag -a v1.0.0 -m "NeoGenesis Core Server v1.0.0"
git push origin v1.0.0
```

## CI Verification
CI runs:
- `./gradlew clean build`
- `./gradlew ktlintCheck`
- `./gradlew detekt`
- `./gradlew test`
- artifact upload (`build/libs/*-all.jar`, `build/distributions/*`)

Ensure workflows pass before announcing the release.
