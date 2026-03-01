# NeoGenesis 2027 - Backlog tecnico ejecutable (12 semanas)

## Estado actual
- DONE: EPIC-SEC-01 IAM JWT/RBAC en HTTP + validacion de rol en gRPC.
- DONE: EPIC-SEC-02 mTLS en gRPC, MQTT y validacion proxy mTLS para HTTP.
- DONE: EPIC-DATA-01 Persistencia JDBC/Flyway para telemetria, comandos, auditoria, gemelo, retina, sesiones, outbox y brechas SLO.
- DONE: EPIC-COMP-01 Matriz ISO 13485 y auditoria por operaciones.
- DONE: EPIC-RET-01/02/CTRL-01/TWIN-01 (plan retina, sesion activa, lazo cerrado, gemelo digital con riesgo).
- DONE: EPIC-CLIN-01/02/03 (ingesta HL7/FHIR/DICOM + consulta por paciente).
- DONE: EPIC-CLIN-04 Parcial (PACS via DICOMweb + HL7 MLLP listener y cliente).
- DONE: EPIC-SLS-01 Outbox con drenado en background, retries exponenciales y DLQ.
- DONE: EPIC-SLS-02 Parcial (publicador cloud AWS SQS configurable por entorno).
- DONE: EPIC-SRE-01/02 (latencia <50ms monitorizada, metrics Prometheus, health endpoints).
- DONE: EPIC-CLIN-04 (DIMSE C-FIND/C-MOVE/C-GET mediante utilidades DIMSE reales).
- DONE: EPIC-CLIN-05 (validacion FHIR/HL7 reforzada por perfiles, versiones y terminologias).
- DONE: EPIC-SEC-03 (OIDC/JWKS + Vault/KMS + cifrado en reposo PHI/PII).
- DONE: EPIC-COMP-02 (gate de trazabilidad ISO 13485 en CI con bloqueo de release).
- DONE: EPIC-COMP-03 (auditoria hash-chain tamper-evident + CAPA/riesgo/DHF digital).
- DONE: EPIC-SRE-03 (OpenTelemetry HTTP/gRPC, correlation IDs, SLO rules y paging base).
- DONE: EPIC-OPS-01 (blue/green + canary + rollback + runbook DR con RTO/RPO).
- DONE: EPIC-SEC-04 (SBOM + escaneo CVE + firma de artefactos en workflow supply-chain).

## Pendiente proxima iteracion
- TODO: EPIC-SEC-05 Rotacion automatica de certificados mTLS con short-lived certs (emision/renovacion CA externa).
- TODO: EPIC-SRE-04 Dashboards de negocio clinico por cohorte y sitio hospitalario en Grafana.

## Criterios minimos de cierre NEOTEC
- API protegida responde `401/403` y gRPC bloquea tokens sin rol permitido.
- Toda telemetria/control tiene traza persistida + evidencia de auditoria.
- Brechas SLO registradas y consultables por API.
- Integracion clinica genera eventos asincronos en outbox.
- Matriz ISO 13485 enlaza requisito, operacion y evidencia de verificacion.
