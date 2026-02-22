# Production Evidence Runbook (v1.0.0)

Este runbook define la secuencia minima para cerrar `GO` con evidencias reales de entorno.

## 1. Supply-chain gate (obligatorio)

Ejecutar workflow:
- `.github/workflows/supply-chain.yml`

Evidencias esperadas:
- SARIF en GitHub Code Scanning.
- Artifact `supply-chain-artifacts` con:
  - `sbom.spdx.json`
  - `grype.sarif`
  - `build/grype-fixable-gate.json`
  - `build/grype-non-fixable-high-critical.json`

Regla de bloqueo:
- PR: bloquea solo `CRITICAL` fixable.
- `main/master`: bloquea `HIGH/CRITICAL` fixable.

## 2. Release readiness (obligatorio para GO)

Ejecutar workflow:
- `.github/workflows/release-readiness.yml`

Inputs para `production`:
- `target_environment=production`
- `run_k8s_checks=true`
- `run_backup_restore=true`
- `run_alerting_checks=true`

Si alguno de esos flags va en `false`, el workflow falla por diseno.

Evidencias esperadas:
- Artifact `release-readiness-evidence-*` con:
  - `security/grype.sarif`
  - `security/sbom.spdx.json`
  - `security/grpc-cert-details.txt`
  - `ops/health-ready.json`
  - `data/restore-validation.txt`
  - `observability/alertmanager-status.json`
  - `summary/release-readiness-summary.md`

## 3. Performance HIL (obligatorio)

Ejecutar workflow:
- `.github/workflows/perf-hil.yml`

Outputs:
- Artifact `k6-latency-evidence` con:
  - `k6-summary.json`
  - `k6-summary.md` (p95/p99 y umbrales)

Gate:
- falla si p95/p99 exceden umbrales de input.

## 4. Canary rollback drill (obligatorio)

Ejecutar workflow:
- `.github/workflows/deploy-canary.yml`

Inputs recomendados para prueba:
- `run_rollback_drill=true`
- `namespace=<namespace objetivo>`
- `rollout_name=neogenesis-core`

Evidencias esperadas:
- Artifact `canary-rollout-evidence-*` con:
  - `rollback/abort.txt`
  - `rollback/status-after-abort.txt`
  - `summary/deploy-canary-summary.md`

## 5. Cierre del checklist

Actualizar:
- `docs/production-go-no-go-checklist.md`

Completar:
- URLs de runs
- Links a artifacts
- Aprobadores Backend/DevOps/Calidad

La release `v1.0.0` solo se marca `GO` cuando todos los P0 tienen evidencia adjunta.
