# NeoGenesis Core Server

NeoGenesis Core Server is the clinical-grade orchestration backend for NeoGenesis S.L. biofabrication systems.

It provides closed-loop control for 3D bioprinting workflows, real-time telemetry processing, digital-twin prediction, clinical interoperability (FHIR/HL7/DICOM), and regulated traceability controls required for medical software operations.

## Company Context

NeoGenesis S.L. develops software infrastructure for regenerative medicine and organ synthesis pipelines.

This server is designed to support:
- High-availability, low-latency control loops
- Secure handling of PHI/PII
- Compliance-by-design (ISO 13485 traceability, auditability, and evidence gating)
- Scalable integration with hospital and cloud ecosystems

## Technical Stack

- Kotlin `2.0.x` (K2)
- Ktor `3.x` (HTTP APIs, auth, middleware)
- gRPC over HTTP/2 (device-control streaming)
- PostgreSQL + Flyway migrations
- MQTT ingestion adapter
- AWS EventBridge / AWS SQS / GCP Pub/Sub outbox publishers
- Prometheus metrics + OpenTelemetry tracing

## Core Capabilities

- Closed-loop telemetry evaluation and control-command emission
- Advanced bio-simulation (`Ostwald-de Waele` + viability decay model)
- Digital twin state updates and risk scoring
- RegenOps gRPC APIs for protocol drafting/versioning, run control, gateway ingestion, and metrics/report export
- Clinical ingestion gateway (FHIR, HL7 v2, DICOM metadata)
- GDPR consent/erasure and retention controls
- ISO 13485 traceability matrix + CI gate
- Regulatory package artifacts (CAPA, risk register, DHF metadata)

## Security and Compliance

- JWT authentication + RBAC authorization (HTTP and gRPC interceptors)
- mTLS support for gRPC and MQTT paths
- Runtime certificate hot-reload for gRPC key material
- PHI/PII encryption at rest (`AES-256 GCM`)
- Optional Vault/KMS secret resolution
- Tamper-evident audit hash chain

## Repository Structure

- `src/main/kotlin/com/neogenesis/server/application` : use cases and services
- `src/main/kotlin/com/neogenesis/server/domain` : core domain models and policies
- `src/main/kotlin/com/neogenesis/server/infrastructure` : adapters (DB, gRPC, MQTT, security, cloud)
- `src/main/kotlin/com/neogenesis/server/presentation` : HTTP route layer
- `src/main/resources/db/migration` : Flyway SQL migrations
- `src/main/resources/iso13485` : traceability matrix and evidence map
- `deploy/` : Kubernetes manifests and runbooks
- `ops/` : monitoring and SLO artifacts
- `docs/regulatory/` : verification and regulatory evidence

## Quick Start (Local)

Prerequisites:
- JDK 21

1. Build and run checks:

```bash
./gradlew clean check
```

2. Run server:

```bash
./gradlew run
```

3. Health endpoint:

```bash
curl http://localhost:8080/health
```

## RegenOps Walking Skeleton (Local)

Prerequisites:
- Docker + Docker Compose
- `grpcurl`

1. Start DB + server:

```bash
docker compose up --build
```

2. Get JWT (bootstrap admin is enabled by default in `docker-compose.yml`):

```powershell
$token = (Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login `
  -ContentType 'application/json' `
  -Body '{"username":"admin","password":"admin-password"}').accessToken
```

3. Create protocol draft:

```bash
grpcurl -plaintext -H "authorization: Bearer $token" \
  -import-path src/main/proto -proto bioprint.proto \
  -d '{"tenant_id":"tenant-a","protocol_id":"proto-1","title":"RegenOps MVP","content_json":"{\"steps\":[\"seed\",\"incubate\"]}","actor_id":"admin"}' \
  localhost:50051 com.neogenesis.grpc.ProtocolService/CreateDraft
```

4. Publish protocol version:

```bash
grpcurl -plaintext -H "authorization: Bearer $token" \
  -import-path src/main/proto -proto bioprint.proto \
  -d '{"tenant_id":"tenant-a","protocol_id":"proto-1","actor_id":"admin","changelog":"initial release"}' \
  localhost:50051 com.neogenesis.grpc.ProtocolService/PublishVersion
```

5. Start run:

```bash
grpcurl -plaintext -H "authorization: Bearer $token" \
  -import-path src/main/proto -proto bioprint.proto \
  -d '{"tenant_id":"tenant-a","protocol_id":"proto-1","protocol_version":1,"run_id":"run-1","gateway_id":"gw-1","actor_id":"admin"}' \
  localhost:50051 com.neogenesis.grpc.RunService/StartRun
```

6. Stream run events:

```bash
grpcurl -plaintext -H "authorization: Bearer $token" \
  -import-path src/main/proto -proto bioprint.proto \
  -d '{"tenant_id":"tenant-a","run_id":"run-1","since_ms":0,"limit":100}' \
  localhost:50051 com.neogenesis.grpc.RunService/StreamRunEvents
```

7. Export run report:

```bash
grpcurl -plaintext -H "authorization: Bearer $token" \
  -import-path src/main/proto -proto bioprint.proto \
  -d '{"tenant_id":"tenant-a","run_id":"run-1"}' \
  localhost:50051 com.neogenesis.grpc.MetricsService/ExportRunReport
```

## Production Baseline

Use `NEOGENESIS_RUNTIME_ENVIRONMENT=production` with valid secrets and certificates.

Mandatory production controls enforced at startup:
- Non-default JWT secret (or valid OIDC/JWKS config)
- Encryption enabled with valid PHI/PII keys
- gRPC mTLS enabled with existing cert/key/trust files
- Non-in-memory database
- Non-logging serverless provider

Reference files:
- `deploy/k8s/base/deployment.yaml`
- `deploy/k8s/base/secrets.example.yaml`
- `deploy/runbooks/production-readiness.md`
- `docs/production-go-no-go-checklist.md`
- `docs/production-evidence-runbook.md`
- `docs/doks-deployment-runbook.md`

## Container Build

Build production image:

```bash
docker build -t neogenesis-core-server:1.0.0 .
```

Run local container:

```bash
docker run --rm -p 8080:8080 -p 50051:50051 neogenesis-core-server:1.0.0
```

## CI / Quality Gates

- `./gradlew test`
- `./gradlew traceabilityGate`
- `./gradlew check`
- `.github/workflows/production-evidence-suite.yml` (orquesta release-readiness + perf-hil + rollback drill)

The traceability gate validates requirement-to-evidence consistency and blocks releases when coverage is incomplete.

## Versioning

Current stable target: `1.0.0`

## Legal

Copyright (c) NeoGenesis S.L.
All rights reserved.
