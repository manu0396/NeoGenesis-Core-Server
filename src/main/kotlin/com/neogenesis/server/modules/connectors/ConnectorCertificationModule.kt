package com.neogenesis.server.modules.connectors

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun Route.connectorCertificationModule(auditTrailService: AuditTrailService) {
    authenticate("auth-jwt") {
        post("/admin/connectors/certify") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)

            val request = call.receive<ConnectorCertificationRequest>()
            if (request.tenantId.isBlank()) {
                throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
            }
            val correlationId =
                call.request.headers["X-Correlation-Id"]
                    ?: call.request.headers["X-Request-Id"]
                    ?: request.correlationId
            if (correlationId.isNullOrBlank()) {
                throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
            }
            if (!principal.tenantId.isNullOrBlank() && principal.tenantId != request.tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "connector.certify.requested",
                    resourceType = "connector",
                    resourceId = request.connectorId,
                    outcome = "accepted",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "tenantId" to request.tenantId,
                            "correlationId" to correlationId,
                            "connectorVersion" to (request.connectorVersion ?: "unknown"),
                        ),
                ),
            )

            call.respond(
                ConnectorCertificationResponse(
                    status = "accepted",
                    connectorId = request.connectorId,
                    correlationId = correlationId,
                ),
            )
        }
    }
}

private fun requireAdminOrFounder(principal: NeoGenesisPrincipal) {
    val roles = principal.roles
    if (!roles.contains("ADMIN") && !roles.contains("FOUNDER")) {
        throw ApiException("forbidden", "admin or founder required", HttpStatusCode.Forbidden)
    }
}

@Serializable
data class ConnectorCertificationRequest(
    val tenantId: String,
    val correlationId: String? = null,
    val connectorId: String,
    val connectorVersion: String? = null,
)

@Serializable
data class ConnectorCertificationResponse(
    val status: String,
    val connectorId: String,
    val correlationId: String,
)
