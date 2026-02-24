package com.neogenesis.server.modules.evidence

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.modules.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.evidencePackModule(
    jobRepository: JobRepository,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    serverVersion: String,
    auditTrailService: AuditTrailService,
) {
    authenticate("auth-jwt") {
        get("/evidence-pack/job/{jobId}/report.csv") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val correlationId = call.requireCorrelationId()
            val tenantId = call.requireTenantId()
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (!job.tenantId.isNullOrBlank() && job.tenantId != tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "evidence.pack.report.export",
                    resourceType = "evidence_pack",
                    resourceId = jobId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-001"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )

            val report =
                EvidenceRunReport.from(
                    jobId = jobId,
                    telemetryRepository = telemetryRepository,
                    twinMetricsRepository = twinMetricsRepository,
                    auditLogRepository = auditLogRepository,
                    serverVersion = serverVersion,
                )
            call.respondText(report.toCsv(), ContentType.Text.CSV)
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireCorrelationId(): String {
    val correlationId =
        request.headers["X-Correlation-Id"]
            ?: request.headers["X-Request-Id"]
    if (correlationId.isNullOrBlank()) {
        throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
    }
    return correlationId
}

private fun io.ktor.server.application.ApplicationCall.requireTenantId(): String {
    val tenantId = request.queryParameters["tenant_id"]
    if (tenantId.isNullOrBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    return tenantId
}
