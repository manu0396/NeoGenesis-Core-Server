package com.neogenesis.server.presentation.http

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.TelemetrySnapshotService
import com.neogenesis.server.application.billing.BillingService
import com.neogenesis.server.application.clinical.ClinicalDimseService
import com.neogenesis.server.application.clinical.ClinicalIntegrationService
import com.neogenesis.server.application.clinical.ClinicalPacsService
import com.neogenesis.server.application.clinical.FhirCohortAnalyticsService
import com.neogenesis.server.application.clinical.Hl7MllpGatewayService
import com.neogenesis.server.application.compliance.ComplianceTraceabilityService
import com.neogenesis.server.application.compliance.GdprService
import com.neogenesis.server.application.compliance.RegulatoryComplianceService
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.DependencyUnavailableException
import com.neogenesis.server.application.port.ControlCommandStore
import com.neogenesis.server.application.port.TelemetryEventStore
import com.neogenesis.server.application.resilience.RequestIdempotencyService
import com.neogenesis.server.application.retina.RetinalPlanningService
import com.neogenesis.server.application.serverless.ServerlessDispatchService
import com.neogenesis.server.application.session.PrintSessionService
import com.neogenesis.server.application.sre.LatencyBudgetService
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.application.twin.DigitalTwinService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.CapaStatus
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommandEvent
import com.neogenesis.server.domain.model.LatencyBreachEvent
import com.neogenesis.server.domain.model.RiskRecord
import com.neogenesis.server.domain.model.TelemetryEvent
import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.domain.model.TraceabilityRequirement
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.infrastructure.security.secured
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.time.Instant
import java.util.Base64
import javax.sql.DataSource

fun Route.healthRoutes(
    grpcPort: Int,
    traceabilityService: ComplianceTraceabilityService,
    dataSource: DataSource,
) {
    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                grpcPort = grpcPort,
                timestamp = Instant.now().toString(),
                requirementsLoaded = traceabilityService.allRequirements().size,
            ),
        )
    }

    get("/health/live") {
        call.respond(HealthProbeResponse(status = "live"))
    }

    get("/health/ready") {
        val checks =
            linkedMapOf(
                "traceability_loaded" to traceabilityService.allRequirements().isNotEmpty(),
                "database_reachable" to isDatabaseReachable(dataSource),
            )
        val ready = checks.values.all { it }
        if (ready) {
            call.respond(HealthProbeResponse(status = "ready", checks = checks))
        } else {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                HealthProbeResponse(status = "not_ready", checks = checks),
            )
        }
    }
}

fun Route.telemetryRoutes(
    telemetrySnapshotService: TelemetrySnapshotService,
    telemetryEventStore: TelemetryEventStore,
    controlCommandStore: ControlCommandStore,
    telemetryProcessingService: TelemetryProcessingService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("operator", "researcher", "controller"), metricsService = metricsService) {
        get("/telemetry") {
            call.respond(telemetrySnapshotService.findAll().map { it.toResponse() })
        }

        get("/telemetry/{printerId}") {
            val printerId = call.parameters["printerId"]
            if (printerId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("printerId is required"))
                return@get
            }

            val telemetry = telemetrySnapshotService.findByPrinterId(printerId)
            if (telemetry == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("No telemetry found for printer $printerId"))
                return@get
            }

            call.respond(telemetry.toResponse())
        }

        get("/telemetry/history") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(telemetryEventStore.recent(limit).map { it.toResponse() })
        }

        get("/commands/history") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(controlCommandStore.recent(limit).map { it.toResponse() })
        }
    }

    secured(requiredRoles = setOf("controller"), metricsService = metricsService) {
        post("/telemetry/evaluate") {
            val request = call.receive<TelemetryEvaluateRequest>()

            val encryptedPayload =
                request.encryptedImageMatrixBase64
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        try {
                            Base64.getDecoder().decode(it)
                        } catch (_: IllegalArgumentException) {
                            call.respond(HttpStatusCode.BadRequest, ApiError("encryptedImageMatrixBase64 must be valid Base64"))
                            return@post
                        }
                    }
                    ?: byteArrayOf()

            val telemetry =
                TelemetryState(
                    printerId = request.printerId,
                    timestampMs = request.timestampMs,
                    nozzleTempCelsius = request.nozzleTempCelsius,
                    extrusionPressureKPa = request.extrusionPressureKPa,
                    cellViabilityIndex = request.cellViabilityIndex,
                    encryptedImageMatrix = encryptedPayload,
                    bioInkViscosityIndex = request.bioInkViscosityIndex,
                    bioInkPh = request.bioInkPh,
                    nirIiTempCelsius = request.nirIiTempCelsius,
                    morphologicalDefectProbability = request.morphologicalDefectProbability,
                    printJobId = request.printJobId,
                    tissueType = request.tissueType,
                )

            val result =
                telemetryProcessingService.process(
                    telemetry = telemetry,
                    source = "http",
                    actor = call.actor(),
                )

            call.respond(
                ControlDecisionResponse(
                    commandId = result.command.commandId,
                    actionType = result.command.actionType,
                    adjustPressure = result.command.adjustPressure,
                    adjustSpeed = result.command.adjustSpeed,
                    reason = result.command.reason,
                ),
            )
        }
    }
}

fun Route.digitalTwinRoutes(
    digitalTwinService: DigitalTwinService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("operator", "researcher", "controller"), metricsService = metricsService) {
        get("/digital-twin") {
            call.respond(digitalTwinService.findAll())
        }

        get("/digital-twin/{printerId}") {
            val printerId = call.parameters["printerId"]
            if (printerId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("printerId is required"))
                return@get
            }

            val state = digitalTwinService.findByPrinterId(printerId)
            if (state == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("No digital twin state for printer $printerId"))
                return@get
            }

            call.respond(state)
        }
    }
}

fun Route.clinicalRoutes(
    clinicalIntegrationService: ClinicalIntegrationService,
    clinicalPacsService: ClinicalPacsService,
    clinicalDimseService: ClinicalDimseService,
    fhirCohortAnalyticsService: FhirCohortAnalyticsService,
    hl7MllpGatewayService: Hl7MllpGatewayService,
    idempotencyService: RequestIdempotencyService,
    requireIdempotencyKey: Boolean,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("clinician", "integrator"), metricsService = metricsService) {
        post("/clinical/fhir") {
            try {
                val request = call.receive<FhirIngestRequest>()
                call.enforceIdempotency(
                    idempotencyService = idempotencyService,
                    operation = "clinical.fhir.ingest",
                    canonicalPayload = request.resourceJson,
                    requireKey = requireIdempotencyKey,
                )
                val doc = clinicalIntegrationService.ingestFhir(request.resourceJson, call.actor())
                call.respond(doc.toResponse())
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid FHIR payload"))
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid FHIR payload"))
            }
        }

        post("/clinical/hl7") {
            try {
                val request = call.receive<Hl7IngestRequest>()
                call.enforceIdempotency(
                    idempotencyService = idempotencyService,
                    operation = "clinical.hl7.ingest",
                    canonicalPayload = request.message,
                    requireKey = requireIdempotencyKey,
                )
                val doc = clinicalIntegrationService.ingestHl7(request.message, call.actor())
                call.respond(doc.toResponse())
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid HL7 payload"))
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid HL7 payload"))
            }
        }

        post("/clinical/dicom") {
            try {
                val request = call.receive<DicomIngestRequest>()
                call.enforceIdempotency(
                    idempotencyService = idempotencyService,
                    operation = "clinical.dicom.ingest",
                    canonicalPayload = request.metadataJson,
                    requireKey = requireIdempotencyKey,
                )
                val doc = clinicalIntegrationService.ingestDicomMetadata(request.metadataJson, call.actor())
                call.respond(doc.toResponse())
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid DICOM metadata payload"))
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, ApiError("Invalid DICOM metadata payload"))
            }
        }

        post("/clinical/pacs/import-latest") {
            val request = call.receive<PacsImportLatestRequest>()
            call.enforceIdempotency(
                idempotencyService = idempotencyService,
                operation = "clinical.pacs.import_latest",
                canonicalPayload = request.patientId,
                requireKey = requireIdempotencyKey,
            )
            val document =
                try {
                    clinicalPacsService.importLatestStudy(
                        patientId = request.patientId,
                        actor = call.actor(),
                    )
                } catch (_: DependencyUnavailableException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("PACS integration is disabled"))
                    return@post
                }
            if (document == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("No PACS study found for patient ${request.patientId}"))
                return@post
            }
            call.respond(document.toResponse())
        }

        post("/clinical/hl7/mllp/send") {
            val request = call.receive<Hl7MllpSendRequest>()
            call.enforceIdempotency(
                idempotencyService = idempotencyService,
                operation = "clinical.hl7.mllp.send",
                canonicalPayload = "${request.host}:${request.port}:${request.message}",
                requireKey = requireIdempotencyKey,
            )
            val ack =
                hl7MllpGatewayService.send(
                    message = request.message,
                    host = request.host,
                    port = request.port,
                    actor = call.actor(),
                )
            call.respond(mapOf("ack" to ack))
        }

        post("/clinical/dimse/cfind") {
            val request = call.receive<DimseCFindRequest>()
            call.enforceIdempotency(
                idempotencyService = idempotencyService,
                operation = "clinical.dimse.cfind",
                canonicalPayload = request.queryKeys.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" },
                requireKey = requireIdempotencyKey,
            )
            val response =
                try {
                    clinicalDimseService.cFind(
                        queryKeys = request.queryKeys,
                        returnKeys = request.returnKeys.ifEmpty { listOf("StudyInstanceUID") },
                    )
                } catch (_: DependencyUnavailableException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("DIMSE integration is disabled"))
                    return@post
                }
            call.respondText(response)
        }

        post("/clinical/dimse/cmove") {
            val request = call.receive<DimseCMoveRequest>()
            call.enforceIdempotency(
                idempotencyService = idempotencyService,
                operation = "clinical.dimse.cmove",
                canonicalPayload = "${request.studyInstanceUid}:${request.destinationAeTitle}",
                requireKey = requireIdempotencyKey,
            )
            val response =
                try {
                    clinicalDimseService.cMove(
                        studyInstanceUid = request.studyInstanceUid,
                        destinationAeTitle = request.destinationAeTitle,
                    )
                } catch (_: DependencyUnavailableException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("DIMSE integration is disabled"))
                    return@post
                }
            call.respondText(response)
        }

        post("/clinical/dimse/cget") {
            val request = call.receive<DimseCGetRequest>()
            call.enforceIdempotency(
                idempotencyService = idempotencyService,
                operation = "clinical.dimse.cget",
                canonicalPayload = request.studyInstanceUid,
                requireKey = requireIdempotencyKey,
            )
            val response =
                try {
                    clinicalDimseService.cGet(
                        studyInstanceUid = request.studyInstanceUid,
                    )
                } catch (_: DependencyUnavailableException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("DIMSE integration is disabled"))
                    return@post
                }
            call.respondText(response)
        }
    }

    secured(requiredRoles = setOf("clinician", "integrator", "auditor"), metricsService = metricsService) {
        get("/clinical/documents") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(clinicalIntegrationService.recent(limit).map { it.toResponse() })
        }

        get("/clinical/documents/{patientId}") {
            val patientId = call.parameters["patientId"]
            if (patientId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("patientId is required"))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(clinicalIntegrationService.findByPatientId(patientId, limit).map { it.toResponse() })
        }

        get("/clinical/pacs/studies/{patientId}") {
            val patientId = call.parameters["patientId"]
            if (patientId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("patientId is required"))
                return@get
            }
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 5
            val payload =
                try {
                    clinicalPacsService.queryStudies(patientId, limit)
                } catch (_: DependencyUnavailableException) {
                    call.respond(HttpStatusCode.ServiceUnavailable, ApiError("PACS integration is disabled"))
                    return@get
                }
            call.respondText(payload)
        }

        get("/clinical/analytics/cohort/demographics") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20_000) ?: 2_000
            call.respond(fhirCohortAnalyticsService.getCohortDemographics(limit))
        }

        get("/clinical/analytics/cohort/viability-by-tissue") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20_000) ?: 2_000
            call.respond(fhirCohortAnalyticsService.getViabilityMetricsByTissue(limit))
        }
    }
}

fun Route.retinaRoutes(
    retinalPlanningService: RetinalPlanningService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("researcher", "clinician", "controller"), metricsService = metricsService) {
        post("/retina/plans/from-dicom") {
            val request = call.receive<RetinaPlanFromDicomRequest>()
            val plan =
                retinalPlanningService.createPlanFromDicom(
                    patientId = request.patientId,
                    sourceDocumentId = request.sourceDocumentId,
                    metadata = request.metadata,
                    actor = call.actor(),
                )
            call.respond(plan)
        }

        get("/retina/plans/{planId}") {
            val planId = call.parameters["planId"]
            if (planId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("planId is required"))
                return@get
            }

            val plan = retinalPlanningService.findByPlanId(planId)
            if (plan == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Retinal plan not found: $planId"))
                return@get
            }

            call.respond(plan)
        }

        get("/retina/plans") {
            val patientId = call.request.queryParameters["patientId"]
            if (!patientId.isNullOrBlank()) {
                val plan = retinalPlanningService.findLatestByPatientId(patientId)
                if (plan == null) {
                    call.respond(HttpStatusCode.NotFound, ApiError("No retinal plan for patient $patientId"))
                    return@get
                }
                call.respond(plan)
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            call.respond(retinalPlanningService.findRecent(limit))
        }
    }
}

fun Route.printSessionRoutes(
    printSessionService: PrintSessionService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("controller", "operator"), metricsService = metricsService) {
        post("/print-sessions") {
            val request = call.receive<CreatePrintSessionRequest>()
            val created =
                printSessionService.create(
                    printerId = request.printerId,
                    planId = request.planId,
                    patientId = request.patientId,
                    actor = call.actor(),
                )
            call.respond(created)
        }

        post("/print-sessions/{sessionId}/activate") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("sessionId is required"))
                return@post
            }
            val session = printSessionService.activate(sessionId, call.actor())
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Session not found: $sessionId"))
                return@post
            }
            call.respond(session)
        }

        post("/print-sessions/{sessionId}/complete") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("sessionId is required"))
                return@post
            }
            val session = printSessionService.complete(sessionId, call.actor())
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Session not found: $sessionId"))
                return@post
            }
            call.respond(session)
        }

        post("/print-sessions/{sessionId}/abort") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("sessionId is required"))
                return@post
            }
            val session = printSessionService.abort(sessionId, call.actor())
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("Session not found: $sessionId"))
                return@post
            }
            call.respond(session)
        }

        get("/print-sessions/active/{printerId}") {
            val printerId = call.parameters["printerId"]
            if (printerId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("printerId is required"))
                return@get
            }
            val session = printSessionService.findActiveByPrinterId(printerId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, ApiError("No active session for printer $printerId"))
                return@get
            }
            call.respond(session)
        }

        get("/print-sessions/active") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
            call.respond(printSessionService.findActive(limit))
        }
    }
}

fun Route.sreRoutes(
    latencyBudgetService: LatencyBudgetService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("sre", "auditor"), metricsService = metricsService) {
        get("/sre/latency-breaches") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 200
            call.respond(latencyBudgetService.recentBreaches(limit).map { it.toResponse() })
        }
    }
}

fun Route.integrationRoutes(
    serverlessDispatchService: ServerlessDispatchService,
    metricsService: OperationalMetricsService,
    outboxBatchSize: Int,
) {
    secured(requiredRoles = setOf("sre", "integrator"), metricsService = metricsService) {
        get("/integration/outbox/pending") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, outboxBatchSize) ?: outboxBatchSize
            call.respond(serverlessDispatchService.pending(limit))
        }

        post("/integration/outbox/{eventId}/ack") {
            val eventId = call.parameters["eventId"]?.toLongOrNull()
            if (eventId == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("eventId must be a valid number"))
                return@post
            }
            serverlessDispatchService.acknowledge(eventId)
            call.respond(HttpStatusCode.OK, mapOf("status" to "processed", "eventId" to eventId.toString()))
        }

        get("/integration/outbox/dead-letter") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(serverlessDispatchService.deadLetter(limit))
        }

        post("/integration/outbox/dead-letter/{deadLetterId}/replay") {
            val deadLetterId = call.parameters["deadLetterId"]?.toLongOrNull()
            if (deadLetterId == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("deadLetterId must be a valid number"))
                return@post
            }
            val replayed = serverlessDispatchService.replayDeadLetter(deadLetterId)
            if (!replayed) {
                call.respond(HttpStatusCode.NotFound, ApiError("Dead-letter event not found: $deadLetterId"))
                return@post
            }
            call.respond(HttpStatusCode.OK, mapOf("status" to "replayed", "deadLetterId" to deadLetterId.toString()))
        }
    }
}

fun Route.complianceRoutes(
    traceabilityService: ComplianceTraceabilityService,
    auditTrailService: AuditTrailService,
    metricsService: OperationalMetricsService,
    billingService: BillingService? = null,
) {
    secured(requiredRoles = setOf("quality_manager", "auditor"), metricsService = metricsService) {
        get("/compliance/traceability") {
            billingService?.requireEntitlement(call.actor(), "compliance:traceability_audit")
            call.respond(
                TraceabilityResponse(
                    requirements = traceabilityService.allRequirements(),
                    operationCoverage = traceabilityService.operationCoverage(),
                ),
            )
        }

        get("/compliance/audit") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 200
            call.respond(auditTrailService.recent(limit).map { it.toResponse() })
        }

        get("/compliance/audit/verify-chain") {
            billingService?.requireEntitlement(call.actor(), "compliance:traceability_audit")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50_000) ?: 10_000
            call.respond(auditTrailService.verifyChain(limit))
        }
    }
}

fun Route.regulatoryRoutes(
    regulatoryComplianceService: RegulatoryComplianceService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("quality_manager", "auditor"), metricsService = metricsService) {
        post("/regulatory/capa") {
            val request = call.receive<CreateCapaRequest>()
            val created =
                regulatoryComplianceService.createCapa(
                    title = request.title,
                    description = request.description,
                    requirementId = request.requirementId,
                    owner = request.owner,
                    actor = call.actor(),
                )
            call.respond(created)
        }

        post("/regulatory/capa/{capaId}/status") {
            val capaId = call.parameters["capaId"]?.toLongOrNull()
            if (capaId == null) {
                call.respond(HttpStatusCode.BadRequest, ApiError("capaId must be a valid number"))
                return@post
            }
            val request = call.receive<UpdateCapaStatusRequest>()
            val status =
                runCatching { CapaStatus.valueOf(request.status.uppercase()) }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, ApiError("Invalid CAPA status"))
                    return@post
                }
            val updated =
                regulatoryComplianceService.updateCapaStatus(
                    capaId = capaId,
                    status = status,
                    actor = call.actor(),
                )
            if (!updated) {
                call.respond(HttpStatusCode.NotFound, ApiError("CAPA not found: $capaId"))
                return@post
            }
            call.respond(mapOf("status" to "updated", "capaId" to capaId.toString(), "newStatus" to status.name))
        }

        get("/regulatory/capa") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(regulatoryComplianceService.listCapas(limit))
        }

        post("/regulatory/risk") {
            val request = call.receive<UpsertRiskRequest>()
            regulatoryComplianceService.upsertRisk(
                record =
                    RiskRecord(
                        riskId = request.riskId,
                        hazardDescription = request.hazardDescription,
                        severity = request.severity,
                        probability = request.probability,
                        detectability = request.detectability,
                        controls = request.controls,
                        residualRiskLevel = request.residualRiskLevel,
                        linkedRequirementId = request.linkedRequirementId,
                        owner = request.owner,
                    ),
                actor = call.actor(),
            )
            call.respond(mapOf("status" to "upserted", "riskId" to request.riskId))
        }

        get("/regulatory/risk") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(regulatoryComplianceService.listRisks(limit))
        }

        post("/regulatory/dhf") {
            val request = call.receive<AddDhfArtifactRequest>()
            val created =
                regulatoryComplianceService.addDhfArtifact(
                    artifactType = request.artifactType,
                    artifactName = request.artifactName,
                    version = request.version,
                    location = request.location,
                    checksumSha256 = request.checksumSha256,
                    approvedBy = request.approvedBy,
                    actor = call.actor(),
                )
            call.respond(created)
        }

        get("/regulatory/dhf") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(regulatoryComplianceService.listDhfArtifacts(limit))
        }
    }
}

fun Route.gdprRoutes(
    gdprService: GdprService,
    metricsService: OperationalMetricsService,
) {
    secured(requiredRoles = setOf("data_protection_officer", "auditor", "clinician"), metricsService = metricsService) {
        post("/gdpr/consent/grant") {
            val request = call.receive<GdprConsentRequest>()
            val record =
                gdprService.grantConsent(
                    patientId = request.patientId,
                    purpose = request.purpose,
                    legalBasis = request.legalBasis,
                    actor = call.actor(),
                )
            call.respond(record)
        }

        post("/gdpr/consent/revoke") {
            val request = call.receive<GdprConsentRequest>()
            val record =
                gdprService.revokeConsent(
                    patientId = request.patientId,
                    purpose = request.purpose,
                    legalBasis = request.legalBasis,
                    actor = call.actor(),
                )
            call.respond(record)
        }

        post("/gdpr/erase/{patientId}") {
            val patientId = call.parameters["patientId"]
            if (patientId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ApiError("patientId is required"))
                return@post
            }
            val request = call.receive<GdprErasureRequest>()
            val record =
                gdprService.recordErasure(
                    patientId = patientId,
                    reason = request.reason,
                    actor = call.actor(),
                )
            call.respond(record)
        }

        get("/gdpr/erasures") {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
            call.respond(gdprService.recentErasures(limit))
        }

        post("/gdpr/retention/enforce") {
            val affected = gdprService.enforceRetention(call.actor())
            call.respond(mapOf("status" to "retention_enforced", "affectedRows" to affected.toString()))
        }
    }
}

fun Route.observabilityRoutes(
    meterRegistry: PrometheusMeterRegistry,
    metricsPath: String,
    metricsService: OperationalMetricsService,
) {
    val sanitizedPath = if (metricsPath.startsWith('/')) metricsPath else "/$metricsPath"

    secured(requiredRoles = setOf("sre", "auditor"), metricsService = metricsService) {
        get(sanitizedPath) {
            call.respondText(meterRegistry.scrape())
        }
    }
}

@Serializable
private data class HealthResponse(
    val status: String,
    val grpcPort: Int,
    val timestamp: String,
    val requirementsLoaded: Int,
)

@Serializable
private data class HealthProbeResponse(
    val status: String,
    val checks: Map<String, Boolean> = emptyMap(),
)

@Serializable
private data class ApiError(
    val error: String,
)

@Serializable
data class TelemetryEvaluateRequest(
    @SerialName("printer_id")
    val printerId: String,
    @SerialName("timestamp_ms")
    val timestampMs: Long,
    @SerialName("nozzle_temp_celsius")
    val nozzleTempCelsius: Float,
    @SerialName("extrusion_pressure_kpa")
    val extrusionPressureKPa: Float,
    @SerialName("cell_viability_index")
    val cellViabilityIndex: Float,
    @SerialName("encrypted_image_matrix_base64")
    val encryptedImageMatrixBase64: String? = null,
    @SerialName("bio_ink_viscosity_index")
    val bioInkViscosityIndex: Float = 0.0f,
    @SerialName("bio_ink_ph")
    val bioInkPh: Float = 7.4f,
    @SerialName("nir_ii_temp_celsius")
    val nirIiTempCelsius: Float = 37.0f,
    @SerialName("morphological_defect_probability")
    val morphologicalDefectProbability: Float = 0.0f,
    @SerialName("print_job_id")
    val printJobId: String = "",
    @SerialName("tissue_type")
    val tissueType: String = "retina",
)

@Serializable
private data class TelemetryResponse(
    val printerId: String,
    val timestampMs: Long,
    val nozzleTempCelsius: Float,
    val extrusionPressureKPa: Float,
    val cellViabilityIndex: Float,
    val encryptedImageMatrixSizeBytes: Int,
    val bioInkViscosityIndex: Float,
    val bioInkPh: Float,
    val nirIiTempCelsius: Float,
    val morphologicalDefectProbability: Float,
    val printJobId: String,
    val tissueType: String,
)

@Serializable
private data class TelemetryEventResponse(
    val telemetry: TelemetryResponse,
    val source: String,
    val createdAtMs: Long,
)

@Serializable
private data class ControlDecisionResponse(
    val commandId: String,
    val actionType: ControlActionType,
    val adjustPressure: Float,
    val adjustSpeed: Float,
    val reason: String,
)

@Serializable
private data class CommandEventResponse(
    val command: ControlDecisionResponse,
    val createdAtMs: Long,
)

@Serializable
private data class FhirIngestRequest(
    val resourceJson: String,
)

@Serializable
private data class Hl7IngestRequest(
    val message: String,
)

@Serializable
private data class DicomIngestRequest(
    val metadataJson: String,
)

@Serializable
private data class PacsImportLatestRequest(
    val patientId: String,
)

@Serializable
private data class Hl7MllpSendRequest(
    val host: String,
    val port: Int,
    val message: String,
)

@Serializable
private data class DimseCFindRequest(
    val queryKeys: Map<String, String>,
    val returnKeys: List<String> = emptyList(),
)

@Serializable
private data class DimseCMoveRequest(
    val studyInstanceUid: String,
    val destinationAeTitle: String,
)

@Serializable
private data class DimseCGetRequest(
    val studyInstanceUid: String,
)

@Serializable
private data class GdprConsentRequest(
    val patientId: String,
    val purpose: String,
    val legalBasis: String,
)

@Serializable
private data class GdprErasureRequest(
    val reason: String,
)

@Serializable
private data class CreateCapaRequest(
    val title: String,
    val description: String,
    val requirementId: String,
    val owner: String,
)

@Serializable
private data class UpdateCapaStatusRequest(
    val status: String,
)

@Serializable
private data class UpsertRiskRequest(
    val riskId: String,
    val hazardDescription: String,
    val severity: Int,
    val probability: Int,
    val detectability: Int,
    val controls: String,
    val residualRiskLevel: Int,
    val linkedRequirementId: String? = null,
    val owner: String,
)

@Serializable
private data class AddDhfArtifactRequest(
    val artifactType: String,
    val artifactName: String,
    val version: String,
    val location: String,
    val checksumSha256: String,
    val approvedBy: String,
)

@Serializable
private data class RetinaPlanFromDicomRequest(
    val patientId: String,
    val sourceDocumentId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
private data class CreatePrintSessionRequest(
    val printerId: String,
    val planId: String,
    val patientId: String,
)

@Serializable
private data class ClinicalIngestResponse(
    val documentType: String,
    val externalId: String?,
    val patientId: String?,
    val createdAtMs: Long,
    val metadata: Map<String, String>,
)

@Serializable
private data class AuditEventResponse(
    val actor: String,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val outcome: String,
    val requirementIds: List<String>,
    val details: Map<String, String>,
    val createdAtMs: Long,
)

@Serializable
private data class TraceabilityResponse(
    val requirements: List<TraceabilityRequirement>,
    val operationCoverage: Map<String, List<String>>,
)

@Serializable
private data class LatencyBreachResponse(
    val printerId: String,
    val source: String,
    val durationMs: Double,
    val thresholdMs: Long,
    val createdAtMs: Long,
)

private fun TelemetryState.toResponse(): TelemetryResponse {
    return TelemetryResponse(
        printerId = printerId,
        timestampMs = timestampMs,
        nozzleTempCelsius = nozzleTempCelsius,
        extrusionPressureKPa = extrusionPressureKPa,
        cellViabilityIndex = cellViabilityIndex,
        encryptedImageMatrixSizeBytes = encryptedImageMatrix.size,
        bioInkViscosityIndex = bioInkViscosityIndex,
        bioInkPh = bioInkPh,
        nirIiTempCelsius = nirIiTempCelsius,
        morphologicalDefectProbability = morphologicalDefectProbability,
        printJobId = printJobId,
        tissueType = tissueType,
    )
}

private fun TelemetryEvent.toResponse(): TelemetryEventResponse {
    return TelemetryEventResponse(
        telemetry = telemetry.toResponse(),
        source = source,
        createdAtMs = createdAtMs,
    )
}

private fun ControlCommandEvent.toResponse(): CommandEventResponse {
    return CommandEventResponse(
        command =
            ControlDecisionResponse(
                commandId = command.commandId,
                actionType = command.actionType,
                adjustPressure = command.adjustPressure,
                adjustSpeed = command.adjustSpeed,
                reason = command.reason,
            ),
        createdAtMs = createdAtMs,
    )
}

private fun ClinicalDocument.toResponse(): ClinicalIngestResponse {
    return ClinicalIngestResponse(
        documentType = documentType.name,
        externalId = externalId,
        patientId = patientId,
        createdAtMs = createdAtMs,
        metadata = metadata,
    )
}

private fun AuditEvent.toResponse(): AuditEventResponse {
    return AuditEventResponse(
        actor = actor,
        action = action,
        resourceType = resourceType,
        resourceId = resourceId,
        outcome = outcome,
        requirementIds = requirementIds,
        details = details,
        createdAtMs = createdAtMs,
    )
}

private fun LatencyBreachEvent.toResponse(): LatencyBreachResponse {
    return LatencyBreachResponse(
        printerId = printerId,
        source = source,
        durationMs = durationMs,
        thresholdMs = thresholdMs,
        createdAtMs = createdAtMs,
    )
}

private fun io.ktor.server.application.ApplicationCall.enforceIdempotency(
    idempotencyService: RequestIdempotencyService,
    operation: String,
    canonicalPayload: String,
    requireKey: Boolean,
) {
    val header = request.headers["Idempotency-Key"]?.trim().orEmpty()
    if (header.isBlank()) {
        if (requireKey) {
            throw BadRequestException(
                code = "missing_idempotency_key",
                message = "Missing Idempotency-Key header",
            )
        }
        return
    }
    idempotencyService.assertOrRemember(
        operation = operation,
        idempotencyKey = header,
        canonicalPayload = canonicalPayload,
    )
}

private fun isDatabaseReachable(dataSource: DataSource): Boolean {
    return runCatching {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SELECT 1")
            }
        }
        true
    }.getOrDefault(false)
}
