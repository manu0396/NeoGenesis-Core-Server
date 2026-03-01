package com.neogenesis.server.modules.admin

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.config.AppConfig
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.sql.DataSource

fun Route.adminWebModule(
    dataSource: DataSource,
    config: AppConfig.AdminWebConfig,
    auditTrailService: AuditTrailService,
) {
    authenticate("auth-jwt") {
        get("/admin/web") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val tenantId = call.requireTenantId()
            val correlationId = requireCorrelationId()
            requireTenantMatch(principal, tenantId)
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
                            "tenantId" to tenantId,
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
            val tenantId = call.requireTenantId()
            val correlationId = requireCorrelationId()
            requireTenantMatch(principal, tenantId)
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
                            "tenantId" to tenantId,
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

        get("/admin/web/login/oidc") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val tenantId = call.requireTenantId()
            val correlationId = requireCorrelationId()
            requireTenantMatch(principal, tenantId)
            val authUrl = config.oidcAuthUrl
            val clientId = config.oidcClientId
            val redirectUri = config.oidcRedirectUri
            if (authUrl.isNullOrBlank() || clientId.isNullOrBlank() || redirectUri.isNullOrBlank()) {
                throw ApiException("oidc_not_configured", "OIDC login not configured", HttpStatusCode.ServiceUnavailable)
            }
            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "admin.web.oidc.login",
                    resourceType = "admin_web",
                    resourceId = "oidc",
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )
            val scope = config.oidcScope?.ifBlank { "openid email profile" } ?: "openid email profile"
            val redirect =
                buildOidcUrl(
                    authUrl = authUrl,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    scope = scope,
                    state = correlationId,
                )
            call.respondText("", status = HttpStatusCode.Found) {
                headers.append("Location", redirect)
            }
        }

        get("/admin/web/gateways") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val tenantId = call.requireTenantId()
            val correlationId = requireCorrelationId()
            requireTenantMatch(principal, tenantId)
            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "admin.web.gateways.list",
                    resourceType = "admin_web",
                    resourceId = tenantId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val gateways = listGateways(dataSource, tenantId, limit)
            call.respond(AdminGatewayResponse(gateways = gateways))
        }

        get("/admin/web/gateways/export") {
            val principal = call.principal<NeoGenesisPrincipal>()
                ?: throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
            requireAdminOrFounder(principal)
            val tenantId = call.requireTenantId()
            val correlationId = requireCorrelationId()
            requireTenantMatch(principal, tenantId)
            auditTrailService.record(
                AuditEvent(
                    actor = call.actor(),
                    action = "admin.web.gateways.export",
                    resourceType = "admin_web",
                    resourceId = tenantId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-006"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 1000
            val gateways = listGateways(dataSource, tenantId, limit)
            val csv = buildGatewayCsv(gateways)
            call.respondText(csv, ContentType.Text.CSV)
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

private fun io.ktor.server.application.ApplicationCall.requireTenantId(): String {
    val tenantId = request.queryParameters["tenant_id"]
    if (tenantId.isNullOrBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    return tenantId
}

private fun requireTenantMatch(principal: NeoGenesisPrincipal, tenantId: String) {
    val principalTenant = principal.tenantId
    if (!principalTenant.isNullOrBlank() && principalTenant != tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
}

private fun listGateways(
    dataSource: DataSource,
    tenantId: String,
    limit: Int,
): List<AdminGatewaySummary> {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, name, created_at
            FROM devices
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setInt(2, limit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AdminGatewaySummary(
                                id = rs.getString("id"),
                                name = rs.getString("name"),
                                tenantId = tenantId,
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun buildGatewayCsv(gateways: List<AdminGatewaySummary>): String {
    val header = "gateway_id,name,tenant_id,created_at"
    val rows = gateways.map { "${it.id},${it.name},${it.tenantId},${it.createdAt}" }
    return (listOf(header) + rows).joinToString("\n")
}

private fun buildOidcUrl(
    authUrl: String,
    clientId: String,
    redirectUri: String,
    scope: String,
    state: String,
): String {
    val params =
        mapOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to scope,
            "state" to state,
        ).entries.joinToString("&") { (k, v) ->
            "${encode(k)}=${encode(v)}"
        }
    return "$authUrl?$params"
}

private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

@Serializable
data class AdminWebStatus(
    val status: String,
    val correlationId: String,
)

@Serializable
data class AdminGatewaySummary(
    val id: String,
    val name: String,
    val tenantId: String,
    val createdAt: String,
)

@Serializable
data class AdminGatewayResponse(
    val gateways: List<AdminGatewaySummary>,
)
