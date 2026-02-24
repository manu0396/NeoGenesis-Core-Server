package com.neogenesis.server.modules.admin

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import javax.sql.DataSource

fun Route.adminApiModule(
    dataSource: DataSource,
    auditTrailService: AuditTrailService,
) {
    authenticate("auth-jwt") {
        get("/admin/roles") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId())
            audit(auditTrailService, context, "admin.roles.list")
            call.respond(AdminRolesResponse(roles = CanonicalRole.entries.map { it.name }))
        }

        get("/admin/users") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId())
            audit(auditTrailService, context, "admin.users.list")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val users = listUsers(dataSource, context.tenantId, limit)
            call.respond(AdminUsersResponse(users = users))
        }

        get("/admin/tenants") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId())
            audit(auditTrailService, context, "admin.tenants.list")
            val tenants = listTenants(dataSource, context)
            call.respond(AdminTenantsResponse(tenants = tenants))
        }

        get("/admin/sites") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId())
            audit(auditTrailService, context, "admin.sites.list")
            val tenants = listTenants(dataSource, context)
            val sites =
                tenants.map { tenantId ->
                    AdminSiteSummary(
                        id = "${tenantId}-default",
                        name = "Default",
                        tenantId = tenantId,
                    )
                }
            call.respond(AdminSitesResponse(sites = sites))
        }
    }
}

private fun requireAdminContext(
    principal: NeoGenesisPrincipal?,
    tenantId: String,
    correlationId: String,
): AdminContext {
    if (principal == null) {
        throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
    }
    val roles = principal.roles
    if (!roles.contains("ADMIN") && !roles.contains("FOUNDER")) {
        throw ApiException("forbidden", "admin or founder required", HttpStatusCode.Forbidden)
    }
    val principalTenant = principal.tenantId
    if (!principalTenant.isNullOrBlank() && principalTenant != tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
    return AdminContext(
        tenantId = tenantId,
        correlationId = correlationId,
        actorId = principal.subject,
    )
}

private fun audit(
    auditTrailService: AuditTrailService,
    context: AdminContext,
    action: String,
) {
    val tenantId = context.tenantId ?: ""
    val correlationId = context.correlationId ?: ""
    if (tenantId.isBlank() || correlationId.isBlank()) {
        throw ApiException("correlation_required", "tenant_id and correlation_id required", HttpStatusCode.BadRequest)
    }
    auditTrailService.record(
        AuditEvent(
            actor = context.actorId,
            action = action,
            resourceType = "admin_api",
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
}

private fun listUsers(
    dataSource: DataSource,
    tenantId: String,
    limit: Int,
): List<AdminUserSummary> {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, username, role, tenant_id, is_active
            FROM users
            WHERE tenant_id = ?
            ORDER BY username ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setInt(2, limit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AdminUserSummary(
                                id = rs.getString("id"),
                                username = rs.getString("username"),
                                role = rs.getString("role"),
                                tenantId = rs.getString("tenant_id"),
                                isActive = rs.getBoolean("is_active"),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun listTenants(
    dataSource: DataSource,
    context: AdminContext,
): List<String> {
    val tenantId = context.tenantId
    return listOf(tenantId)
}

data class AdminContext(
    val tenantId: String,
    val correlationId: String,
    val actorId: String,
)

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

@Serializable
data class AdminRolesResponse(
    val roles: List<String>,
)

@Serializable
data class AdminUserSummary(
    val id: String,
    val username: String,
    val role: String,
    val tenantId: String?,
    val isActive: Boolean,
)

@Serializable
data class AdminUsersResponse(
    val users: List<AdminUserSummary>,
)

@Serializable
data class AdminTenantsResponse(
    val tenants: List<String>,
)

@Serializable
data class AdminSiteSummary(
    val id: String,
    val name: String,
    val tenantId: String,
)

@Serializable
data class AdminSitesResponse(
    val sites: List<AdminSiteSummary>,
)
