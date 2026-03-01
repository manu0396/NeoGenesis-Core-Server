package com.neogenesis.server.modules

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.DeviceRepository
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.infrastructure.security.tenantId
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

fun Route.devicesModule(
    deviceRepository: DeviceRepository,
    auditLogRepository: AuditLogRepository,
    metrics: InMemoryMetrics,
) {
    authenticate("auth-jwt") {
        route("/devices") {
            post {
                val principal = call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
                val request = call.receive<CreateDeviceRequest>()
                if (request.id.isBlank() || request.name.isBlank()) {
                    throw ApiException("invalid_request", "Device id and name are required", HttpStatusCode.BadRequest)
                }

                val device =
                    deviceRepository.create(
                        id = request.id.trim(),
                        tenantId = request.tenantId?.trim()?.ifBlank { null } ?: call.tenantId(),
                        name = request.name.trim(),
                    )

                auditLogRepository.append(
                    jobId = null,
                    actorId = principal.subject,
                    eventType = "device.created",
                    deviceId = device.id,
                    payload =
                        buildJsonObject {
                            put("deviceId", device.id)
                            put("tenantId", device.tenantId ?: "")
                            put("name", device.name)
                            put("createdAt", device.createdAt.toString())
                        },
                )
                metrics.increment("device_create_total")
                call.respond(HttpStatusCode.Created, device.toResponse())
            }

            get {
                call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR, CanonicalRole.INTEGRATION)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 1000) ?: 100
                val devices = deviceRepository.list(call.tenantId(), limit).map { it.toResponse() }
                call.respond(HttpStatusCode.OK, devices)
            }
        }
    }
}

@Serializable
data class CreateDeviceRequest(
    val id: String,
    val tenantId: String? = null,
    val name: String,
)

@Serializable
data class DeviceResponse(
    val id: String,
    val tenantId: String?,
    val name: String,
    val createdAt: String,
)

private fun com.neogenesis.server.infrastructure.persistence.DeviceRecord.toResponse(): DeviceResponse {
    return DeviceResponse(
        id = id,
        tenantId = tenantId,
        name = name,
        createdAt = createdAt.toString(),
    )
}
