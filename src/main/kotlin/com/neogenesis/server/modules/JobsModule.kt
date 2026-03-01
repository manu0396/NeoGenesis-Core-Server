package com.neogenesis.server.modules

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.DeviceRepository
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.security.enforceRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.jobsModule(
    deviceRepository: DeviceRepository,
    jobRepository: JobRepository,
    auditLogRepository: AuditLogRepository,
    metrics: InMemoryMetrics,
) {
    authenticate("auth-jwt") {
        route("/jobs") {
            post {
                val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.INTEGRATION)
                val request = call.receive<CreateJobRequest>()
                if (request.id.isBlank() || request.deviceId.isBlank()) {
                    throw ApiException("invalid_request", "Job id and deviceId are required", HttpStatusCode.BadRequest)
                }
                val device =
                    deviceRepository.find(request.deviceId.trim())
                        ?: throw ApiException("device_not_found", "Device not found", HttpStatusCode.NotFound)

                val job =
                    jobRepository.create(
                        id = request.id.trim(),
                        deviceId = device.id,
                        tenantId = request.tenantId?.trim()?.ifBlank { null },
                        status = "CREATED",
                    )
                auditLogRepository.append(
                    jobId = job.id,
                    actorId = principal.subject,
                    eventType = "job.created",
                    deviceId = device.id,
                    payload =
                        buildJsonObject {
                            put("jobId", job.id)
                            put("deviceId", job.deviceId)
                            put("tenantId", job.tenantId ?: "")
                            put("status", job.status)
                        },
                )
                metrics.increment("job_create_total")
                call.respond(HttpStatusCode.Created, job.toResponse())
            }

            get {
                call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR, CanonicalRole.INTEGRATION)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
                call.respond(jobRepository.list(limit).map { it.toResponse() })
            }
        }

        get("/jobs/{jobId}") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR, CanonicalRole.INTEGRATION)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            call.respond(HttpStatusCode.OK, job.toResponse())
        }

        post("/jobs/{jobId}/status") {
            val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val request = call.receive<UpdateJobStatusRequest>()
            val status = request.status.trim().uppercase()
            if (status !in setOf("CREATED", "RUNNING", "PAUSED", "COMPLETED", "FAILED", "CANCELLED")) {
                throw ApiException("invalid_status", "Unsupported job status", HttpStatusCode.BadRequest)
            }
            val updated =
                jobRepository.updateStatus(jobId, status)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)

            auditLogRepository.append(
                jobId = updated.id,
                actorId = principal.subject,
                eventType = "job.status_updated",
                deviceId = updated.deviceId,
                payload =
                    buildJsonObject {
                        put("jobId", updated.id)
                        put("status", updated.status)
                    },
            )
            metrics.increment("job_status_update_total")
            call.respond(HttpStatusCode.OK, updated.toResponse())
        }
    }
}

@Serializable
data class CreateJobRequest(
    val id: String,
    val deviceId: String,
    val tenantId: String? = null,
)

@Serializable
data class UpdateJobStatusRequest(
    val status: String,
)

@Serializable
data class JobResponse(
    val id: String,
    val deviceId: String,
    val tenantId: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

private fun com.neogenesis.server.infrastructure.persistence.JobRecord.toResponse(): JobResponse {
    return JobResponse(
        id = id,
        deviceId = deviceId,
        tenantId = tenantId,
        status = status,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )
}
