package com.neogenesis.server.modules

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.infrastructure.security.tenantId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Route.bioinkModule(
    jobRepository: JobRepository,
    auditLogRepository: AuditLogRepository,
    metrics: InMemoryMetrics,
) {
    authenticate("auth-jwt") {
        post("/bioink/job/{jobId}/batch") {
            val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(call.tenantId(), jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            val request = call.receive<RecordBioinkBatchRequest>()

            val event =
                auditLogRepository.append(
                    jobId = job.id,
                    actorId = principal.subject,
                    eventType = "bioink.batch.recorded",
                    deviceId = job.deviceId,
                    payload =
                        buildJsonObject {
                            put("batchId", request.batchId)
                            put("viscosityIndex", request.viscosityIndex)
                            put("ph", request.ph)
                            put("notes", request.notes ?: "")
                        },
                )
            metrics.increment("bioink_batch_record_total")
            call.respond(
                HttpStatusCode.Accepted,
                mapOf(
                    "status" to "recorded",
                    "auditEventId" to event.id.toString(),
                    "jobId" to job.id,
                ),
            )
        }

        get("/bioink/job/{jobId}/batch") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val events =
                auditLogRepository.listByJob(jobId, 1000)
                    .filter { it.eventType == "bioink.batch.recorded" }
                    .map {
                        BioinkBatchAuditResponse(
                            auditId = it.id,
                            jobId = it.jobId ?: "",
                            deviceId = it.deviceId,
                            payloadJson = it.payloadJson,
                            createdAt = it.createdAt.toString(),
                        )
                    }
            call.respond(HttpStatusCode.OK, events)
        }
    }
}

@Serializable
data class RecordBioinkBatchRequest(
    val batchId: String,
    val viscosityIndex: Double,
    val ph: Double,
    val notes: String? = null,
)

@Serializable
data class BioinkBatchAuditResponse(
    val auditId: Long,
    val jobId: String,
    val deviceId: String?,
    val payloadJson: String,
    val createdAt: String,
)
