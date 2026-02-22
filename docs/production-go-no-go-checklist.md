# Go/No-Go Produccion NeoGenesis v1.0.0

Fecha de evaluacion repositorio: `2026-02-22`  
Release candidate: `v1.0.0`  
Owner de release: `pendiente`

## Criterio de decision
- `GO`: todos los P0 en verde con evidencia de entorno real.
- `NO-GO`: cualquier P0 en rojo o sin evidencia verificable.

## P0 Seguridad y Cumplimiento
- [x] JWT y RBAC validados en HTTP y gRPC con tests de regresion.
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/security/SecurityPluginsTest.kt`
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/grpc/GrpcTelemetryIntegrationTest.kt`
- [ ] gRPC mTLS activo en entorno objetivo y certificados validos.
Ref evidencia: artifact `release-readiness-evidence-*` -> `security/grpc-cert-details.txt`
- [ ] Cifrado PHI/PII activo con claves no default.
Ref validacion de arranque: `src/main/kotlin/com/neogenesis/server/infrastructure/config/ProductionReadinessValidator.kt`
- [ ] Escaneo supply-chain en CI sin `HIGH/CRITICAL` fixables.
Ref: `.github/workflows/supply-chain.yml`
- [x] SARIF subido a GitHub Code Scanning en el pipeline.
Ref: `.github/workflows/supply-chain.yml`
- [x] Excepciones temporales de CVE documentadas en `SECURITY.md` (si aplica).
Ref: `SECURITY.md`

## P0 Fiabilidad Operativa
- [x] Outbox sin duplicados con multiples replicas (claim `PROCESSING` validado).
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/persistence/JdbcOutboxEventStoreClaimTest.kt`
- [x] Idempotencia funcional (mismo key+payload OK, key+payload distinto = `409`).
Ref: `src/test/kotlin/com/neogenesis/server/application/resilience/RequestIdempotencyServiceTest.kt`
- [x] Manejo de errores HTTP/gRPC consistente y trazable.
Ref: `src/main/kotlin/com/neogenesis/server/Application.kt`
Ref: `src/main/kotlin/com/neogenesis/server/infrastructure/grpc/BioPrintGrpcService.kt`
- [x] Readiness valida DB y trazabilidad, no solo liveness.
Ref: `src/main/kotlin/com/neogenesis/server/presentation/http/Routes.kt`
- [ ] Prueba de rollback canary ejecutada con evidencia.
Ref: `.github/workflows/deploy-canary.yml` (`run_rollback_drill=true`)

## P0 Datos y Persistencia
- [x] Migraciones Flyway aplican en limpio y sobre entorno actualizado.
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/persistence/DatabaseFactoryMigrationTest.kt`
- [ ] Backups de PostgreSQL definidos y restauracion probada (RTO/RPO documentados).
Ref: `deploy/runbooks/disaster-recovery.md`
Ref evidencia: artifact `release-readiness-evidence-*` -> `data/restore-validation.txt`
- [ ] Retencion/anomizacion GDPR ejecutada y auditable.
Ref endpoint: `POST /gdpr/retention/enforce`
Ref evidencia: log/audit de ejecucion en entorno objetivo

## P0 Observabilidad
- [x] Logs con `correlation_id`, `method`, `path` en produccion.
Ref: `src/main/resources/logback.xml`
- [ ] Metricas `/metrics` scrapeadas por Prometheus.
Ref: `ops/monitoring/prometheus-rules.yml`
Ref evidencia: snapshot y target scrape en entorno
- [ ] Alertas SLO activas y con on-call confirmado.
Ref: `ops/monitoring/prometheus-rules.yml`
Ref evidencia: `alertmanager-status.json` + prueba de disparo controlada

## P0 Pruebas Minimas
- [x] `./gradlew clean check` en verde.
Resultado local: `2026-02-22`
- [x] Tests unitarios y de integracion minima (DB + gRPC) en verde.
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/grpc/GrpcTelemetryIntegrationTest.kt`
Ref: `src/test/kotlin/com/neogenesis/server/infrastructure/persistence/DatabaseFactoryMigrationTest.kt`

## Evidencias obligatorias para cierre
- Pipeline run URL (`release-readiness`):
- Reporte de seguridad (SARIF):
- Artefacto SBOM:
- Resultado de prueba de carga p95/p99 (`perf-hil`):
- Evidencia de restauracion backup:
- Evidencia rollback canary:

## Decision final
- Estado actual: `NO-GO (pendiente de evidencias de entorno)`
- Estado objetivo tras evidencia: `GO`
- Aprobadores:
  - Backend:
  - DevOps/SRE:
  - Calidad/Regulatorio:
