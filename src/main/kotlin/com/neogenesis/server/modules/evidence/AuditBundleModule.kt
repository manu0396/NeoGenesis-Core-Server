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
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.auditBundleModule(
    jobRepository: JobRepository,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    serverVersion: String,
    auditTrailService: AuditTrailService,
) {
    authenticate("auth-jwt") {
        get("/audit-bundle/job/{jobId}.zip") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val correlationId = call.requireCorrelationId()
            val tenantId = call.requireTenantId()
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(tenantId, jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (!job.tenantId.isNullOrBlank() && job.tenantId != tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            val bundle =
                AuditBundleBuilder.build(
                    jobId = jobId,
                    tenantId = tenantId,
                    telemetryRepository = telemetryRepository,
                    twinMetricsRepository = twinMetricsRepository,
                    auditLogRepository = auditLogRepository,
                    serverVersion = serverVersion,
                )

            auditTrailService.record(
                AuditEvent(
                    tenantId = tenantId,
                    actor = call.actor(),
                    action = "audit.bundle.export",
                    resourceType = "audit_bundle",
                    resourceId = jobId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-001"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                            "bundleHash" to bundle.manifest.bundleHash,
                        ),
                ),
            )

            call.respondBytes(
                bytes = bundle.bytes,
                contentType = ContentType.Application.Zip,
            )
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
