package com.neogenesis.server.modules.admin

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.modules.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

fun Route.adminWebModule(
    auditTrailService: AuditTrailService,
) {
    authenticate("auth-jwt") {
        get("/admin/web") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val correlationId = requireCorrelationId()
            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "admin.web.view",
                    resourceType = "admin_web",
                    resourceId = "landing",
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "correlationId" to correlationId,
                        ),
                ),
            )
            call.respondText(
                text =
                    """
                        <!doctype html>
                        <html lang="en">
                          <head>
                            <meta charset="utf-8">
                            <title>NeoGenesis Admin</title>
                          </head>
                          <body>
                            <h1>NeoGenesis Admin Console</h1>
                            <p>Placeholder shell for enterprise admin UI.</p>
                          </body>
                        </html>
                    """.trimIndent(),
                contentType = ContentType.Text.Html,
            )
        }

        get("/admin/web/status") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val correlationId = requireCorrelationId()
            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "admin.web.status",
                    resourceType = "admin_web",
                    resourceId = "status",
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "correlationId" to correlationId,
                        ),
                ),
            )
            call.respond(
                AdminWebStatus(
                    status = "ok",
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

private fun io.ktor.server.application.ApplicationCall.requireCorrelationId(): String {
    val correlationId =
        request.headers["X-Correlation-Id"]
            ?: request.headers["X-Request-Id"]
    if (correlationId.isNullOrBlank()) {
        throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
    }
    return correlationId
}

@Serializable
data class AdminWebStatus(
    val status: String,
    val correlationId: String,
)
