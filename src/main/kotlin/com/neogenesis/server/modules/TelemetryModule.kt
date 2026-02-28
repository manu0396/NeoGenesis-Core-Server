package com.neogenesis.server.modules

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

fun Route.telemetryModule(
    jobRepository: JobRepository,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    metrics: InMemoryMetrics,
    maxPayloadBytes: Int,
) {
    authenticate("auth-jwt") {
        post("/telemetry/job/{jobId}") {
            val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.INTEGRATION)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }

            val request = call.receive<JobPayloadIngestRequest>()
            val job =
                jobRepository.get(call.tenantId(), jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (job.deviceId != request.deviceId) {
                throw ApiException("device_mismatch", "deviceId does not match job device", HttpStatusCode.BadRequest)
            }

            val payloadJson = Json.encodeToString(JsonElement.serializer(), request.payload)
            if (payloadJson.toByteArray(Charsets.UTF_8).size > maxPayloadBytes) {
                throw ApiException("payload_too_large", "Payload exceeds limit", HttpStatusCode.PayloadTooLarge)
            }

            val recordedAt = request.timestamp?.let(Instant::ofEpochMilli) ?: Instant.now()
            val inserted =
                telemetryRepository.insert(
                    jobId = job.id,
                    deviceId = job.deviceId,
                    payload = request.payload,
                    recordedAt = recordedAt,
                )
            auditLogRepository.append(
                jobId = job.id,
                actorId = principal.subject,
                eventType = "telemetry.ingested",
                deviceId = job.deviceId,
                payload =
                    buildJsonObject {
                        put("telemetryRecordId", inserted.id)
                        put("recordedAt", recordedAt.toEpochMilli())
                    },
            )
            metrics.increment("telemetry_ingest_total")
            call.respond(HttpStatusCode.Accepted, IngestAcceptedResponse(status = "accepted", id = inserted.id.toString()))
        }

        post("/twin/job/{jobId}") {
            val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.INTEGRATION)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }

            val request = call.receive<JobPayloadIngestRequest>()
            val job =
                jobRepository.get(call.tenantId(), jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (job.deviceId != request.deviceId) {
                throw ApiException("device_mismatch", "deviceId does not match job device", HttpStatusCode.BadRequest)
            }

            val payloadJson = Json.encodeToString(JsonElement.serializer(), request.payload)
            if (payloadJson.toByteArray(Charsets.UTF_8).size > maxPayloadBytes) {
                throw ApiException("payload_too_large", "Payload exceeds limit", HttpStatusCode.PayloadTooLarge)
            }

            val recordedAt = request.timestamp?.let(Instant::ofEpochMilli) ?: Instant.now()
            val inserted =
                twinMetricsRepository.insert(
                    jobId = job.id,
                    deviceId = job.deviceId,
                    payload = request.payload,
                    recordedAt = recordedAt,
                )
            auditLogRepository.append(
                jobId = job.id,
                actorId = principal.subject,
                eventType = "twin.ingested",
                deviceId = job.deviceId,
                payload =
                    buildJsonObject {
                        put("twinMetricId", inserted.id)
                        put("recordedAt", recordedAt.toEpochMilli())
                    },
            )
            metrics.increment("twin_ingest_total")
            call.respond(HttpStatusCode.Accepted, IngestAcceptedResponse(status = "accepted", id = inserted.id.toString()))
        }

        get("/telemetry/job/{jobId}") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR, CanonicalRole.INTEGRATION)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val from = call.request.queryParameters["from"]?.toLongOrNull()?.let(Instant::ofEpochMilli)
            val to = call.request.queryParameters["to"]?.toLongOrNull()?.let(Instant::ofEpochMilli)
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 5000) ?: 100
            val records =
                telemetryRepository.listByJob(jobId, from, to, limit).map {
                    TelemetryRecordResponse(
                        id = it.id,
                        jobId = it.jobId,
                        deviceId = it.deviceId,
                        payloadJson = it.payloadJson,
                        recordedAt = it.recordedAt.toEpochMilli(),
                    )
                }
            call.respond(HttpStatusCode.OK, records)
        }
    }
}

@Serializable
data class JobPayloadIngestRequest(
    val deviceId: String,
    val payload: JsonElement,
    val timestamp: Long? = null,
)

@Serializable
data class IngestAcceptedResponse(
    val status: String,
    val id: String,
)

@Serializable
data class TelemetryRecordResponse(
    val id: Long,
    val jobId: String,
    val deviceId: String,
    val payloadJson: String,
    val recordedAt: Long,
)
