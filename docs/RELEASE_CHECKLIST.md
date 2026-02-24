# Release Checklist (v1.0.0)

## Pre-flight
- Ensure `COMMERCIAL_MODE` is OFF by default in production.
- Confirm DB migrations applied (`V11__commercial_pipeline.sql`).

## Build (no tests)
```
./gradlew assemble :gateway:assemble :contracts:assemble checkContracts
```

## Tests
```
./gradlew build :gateway:test
```

## Publish Contracts (Maven Local)
```
./gradlew publishContractsPublicationToMavenLocal
```

## Docker Images
```
# Server
docker build -t neogenesis-core-server:1.0.0 .

# Gateway
docker build -t neogenesis-gateway:1.0.0 -f gateway/Dockerfile .
```

## Tag Release
```
git tag v1.0.0
git push origin v1.0.0
```

## Notes
- Contracts artifact: `com.neogenesis:neogenesis-contracts:1.0.0`
- Gateway uses `gateway/config/gateway.env.example` for env template.
