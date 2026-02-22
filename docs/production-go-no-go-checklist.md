# Go/No-Go Produccion NeoGenesis v1.0.0

Fecha de evaluacion: `YYYY-MM-DD`  
Release candidate: `v1.0.0-rcX`  
Owner de release: `...`

## Criterio de decision
- `GO`: todos los P0 en verde.
- `NO-GO`: cualquier P0 en rojo.

## P0 Seguridad y Cumplimiento
- [ ] JWT y RBAC validados en HTTP y gRPC con tests de regresion.
- [ ] gRPC mTLS activo en entorno objetivo y certificados validos.
- [ ] Cifrado PHI/PII activo con claves no default.
- [ ] Escaneo supply-chain en CI sin `HIGH/CRITICAL` fixables.
- [ ] SARIF subido a GitHub Code Scanning en el pipeline.
- [ ] Excepciones temporales de CVE documentadas en `SECURITY.md` (si aplica).

## P0 Fiabilidad Operativa
- [ ] Outbox sin duplicados con multiples replicas (claim `PROCESSING` validado).
- [ ] Idempotencia funcional (mismo key+payload OK, key+payload distinto = `409`).
- [ ] Manejo de errores HTTP/gRPC consistente y trazable.
- [ ] Readiness valida DB y trazabilidad, no solo liveness.
- [ ] Prueba de rollback canary ejecutada con evidencia.

## P0 Datos y Persistencia
- [ ] Migraciones Flyway aplican en limpio y sobre entorno actualizado.
- [ ] Backups de PostgreSQL definidos y restauracion probada (RTO/RPO documentados).
- [ ] Retencion/anomizacion GDPR ejecutada y auditable.

## P0 Observabilidad
- [ ] Logs con `correlation_id`, `method`, `path` en produccion.
- [ ] Metricas `/metrics` scrapeadas por Prometheus.
- [ ] Alertas SLO activas y con on-call confirmado.

## P0 Pruebas Minimas
- [ ] `./gradlew clean check` en verde.
- [ ] Tests unitarios y de integracion minima (DB + gRPC) en verde.

## Evidencias obligatorias
- Pipeline run URL:
- Reporte de seguridad (SARIF):
- Artefacto SBOM:
- Resultado de prueba de carga p95/p99:
- Evidencia de restauracion backup:

## Decision final
- Estado: `GO / NO-GO`
- Aprobadores:
  - Backend:
  - DevOps/SRE:
  - Calidad/Regulatorio:
