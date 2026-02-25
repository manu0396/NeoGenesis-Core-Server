package com.neogenesis.server.modules.admin

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.regenops.ProtocolPublishApproval
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JdbcRegenOpsStore
import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal
import com.neogenesis.server.modules.ApiException
import com.neogenesis.server.modules.PasswordService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

fun Route.adminApiModule(
    dataSource: DataSource,
    auditTrailService: AuditTrailService,
    passwordService: PasswordService,
    regenOpsService: RegenOpsService,
    complianceEnabled: Boolean,
) {
    val evidenceStore = JdbcRegenOpsStore(dataSource)
    authenticate("auth-jwt") {
        get("/admin/roles") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.roles.list")
            call.respond(AdminRolesResponse(roles = listOf("ADMIN", "OPERATOR", "VIEWER")))
        }

        get("/admin/users") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.users.list")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val users = listUsers(dataSource, context.tenantId, limit)
            call.respond(AdminUsersResponse(users = users))
        }

        post("/admin/users") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
            val request = call.receive<AdminUserCreateRequest>()
            val tenantId = normalizeTenant(request.tenantId, context)
            val username = request.username.trim()
            val password = request.password
            val role = request.role.trim().uppercase()
            if (username.isBlank() || password.isBlank()) {
                throw ApiException("invalid_request", "username and password are required", HttpStatusCode.BadRequest)
            }
            val normalizedRole = normalizeRoleForStorage(role)
            val userId = createUser(dataSource, username, passwordService.hash(password), normalizedRole, tenantId)
            val response =
                AdminUserSummary(
                    id = userId,
                    username = username,
                    role = normalizeRoleForResponse(normalizedRole),
                    tenantId = tenantId,
                    isActive = true,
                )
            auditMutation(auditTrailService, context, "admin.users.create", "user", userId, mapOf("username" to username, "role" to normalizedRole))
            evidenceMutation(evidenceStore, context, "admin.users.create", "user", userId, mapOf("username" to username, "role" to normalizedRole))
            call.respond(response)
        }

        put("/admin/users/{userId}/role") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
            val userId = call.parameters["userId"]?.trim().orEmpty()
            if (userId.isBlank()) {
                throw ApiException("invalid_request", "userId is required", HttpStatusCode.BadRequest)
            }
            val request = call.receive<AdminUserRoleUpdateRequest>()
            val role = normalizeRoleForStorage(request.role.trim().uppercase())
            val updated = updateUserRole(dataSource, userId, role)
            auditMutation(auditTrailService, context, "admin.users.role.update", "user", userId, mapOf("role" to role))
            evidenceMutation(evidenceStore, context, "admin.users.role.update", "user", userId, mapOf("role" to role))
            call.respond(updated)
        }

        get("/admin/tenants") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.tenants.list")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val tenants = listTenants(dataSource, context, limit)
            call.respond(AdminTenantsResponse(tenants = tenants))
        }

        post("/admin/tenants") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
            val request = call.receive<AdminTenantCreateRequest>()
            val tenantId = request.tenantId.trim()
            val name = request.name?.trim()?.ifBlank { tenantId } ?: tenantId
            if (tenantId.isBlank()) {
                throw ApiException("invalid_request", "tenantId is required", HttpStatusCode.BadRequest)
            }
            val created = createTenant(dataSource, tenantId, name)
            auditMutation(auditTrailService, context, "admin.tenants.create", "tenant", tenantId, mapOf("name" to name))
            evidenceMutation(evidenceStore, context, "admin.tenants.create", "tenant", tenantId, mapOf("name" to name))
            call.respond(created)
        }

        get("/admin/sites") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.sites.list")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val sites = listSites(dataSource, context.tenantId, limit)
            call.respond(AdminSitesResponse(sites = sites))
        }

        post("/admin/sites") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
            val request = call.receive<AdminSiteCreateRequest>()
            val tenantId = normalizeTenant(request.tenantId, context)
            val siteId = request.siteId.trim()
            val name = request.name.trim()
            if (siteId.isBlank() || name.isBlank()) {
                throw ApiException("invalid_request", "siteId and name are required", HttpStatusCode.BadRequest)
            }
            val created = createSite(dataSource, tenantId, siteId, name)
            auditMutation(auditTrailService, context, "admin.sites.create", "site", siteId, mapOf("tenantId" to tenantId, "name" to name))
            evidenceMutation(evidenceStore, context, "admin.sites.create", "site", siteId, mapOf("tenantId" to tenantId, "name" to name))
            call.respond(created)
        }

        get("/admin/gateways") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.gateways.list")
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
            val gateways = listGatewayInventory(dataSource, context.tenantId, limit)
            call.respond(AdminGatewayResponse(gateways = gateways))
        }

        get("/admin/feature-flags") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = false)
            audit(auditTrailService, context, "admin.featureFlags.list")
            val flags = listFeatureFlags(dataSource, context.tenantId)
            call.respond(AdminFeatureFlagsResponse(flags = flags))
        }

        put("/admin/feature-flags") {
            val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
            val request = call.receive<AdminFeatureFlagUpsertRequest>()
            val tenantId = normalizeTenant(request.tenantId, context)
            val key = request.key.trim()
            if (key.isBlank()) {
                throw ApiException("invalid_request", "key is required", HttpStatusCode.BadRequest)
            }
            val updated = upsertFeatureFlag(dataSource, tenantId, key, request.enabled, context.actorId)
            auditMutation(auditTrailService, context, "admin.featureFlags.upsert", "feature_flag", key, mapOf("tenantId" to tenantId, "enabled" to request.enabled.toString()))
            evidenceMutation(evidenceStore, context, "admin.featureFlags.upsert", "feature_flag", key, mapOf("tenantId" to tenantId, "enabled" to request.enabled.toString()))
            call.respond(updated)
        }

        if (complianceEnabled) {
            post("/admin/compliance/protocols/{protocolId}/publish-approvals") {
                val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
                val protocolId = call.parameters["protocolId"]?.trim().orEmpty()
                if (protocolId.isBlank()) {
                    throw ApiException("invalid_request", "protocolId is required", HttpStatusCode.BadRequest)
                }
                val request = call.receive<ComplianceApprovalRequest>()
                val approval =
                    regenOpsService.requestPublishApproval(
                        tenantId = context.tenantId,
                        protocolId = protocolId,
                        actorId = context.actorId,
                        reason = request.reason,
                    )
                auditMutation(
                    auditTrailService,
                    context,
                    "admin.compliance.protocol.publish.approval.requested",
                    "protocol_approval",
                    approval.id,
                    mapOf("protocolId" to protocolId),
                )
                call.respond(ComplianceApprovalResponse.from(approval))
            }

            post("/admin/compliance/publish-approvals/{approvalId}/approve") {
                val context = requireAdminContext(call.principal(), call.requireTenantId(), call.requireCorrelationId(), requireAdmin = true)
                val approvalId = call.parameters["approvalId"]?.trim().orEmpty()
                if (approvalId.isBlank()) {
                    throw ApiException("invalid_request", "approvalId is required", HttpStatusCode.BadRequest)
                }
                val request = call.receive<ComplianceApprovalDecisionRequest>()
                val approval =
                    regenOpsService.approvePublishApproval(
                        tenantId = context.tenantId,
                        approvalId = approvalId,
                        actorId = context.actorId,
                        comment = request.comment,
                    )
                auditMutation(
                    auditTrailService,
                    context,
                    "admin.compliance.protocol.publish.approval.approved",
                    "protocol_approval",
                    approval.id,
                    mapOf("protocolId" to approval.protocolId),
                )
                call.respond(ComplianceApprovalResponse.from(approval))
            }
        }
    }
}

private fun requireAdminContext(
    principal: NeoGenesisPrincipal?,
    tenantId: String,
    correlationId: String,
    requireAdmin: Boolean,
): AdminContext {
    if (principal == null) {
        throw ApiException("unauthorized", "unauthorized", HttpStatusCode.Unauthorized)
    }
    val accessRole = resolveAdminRole(principal.roles)
    if (requireAdmin && accessRole != AdminAccessRole.ADMIN) {
        throw ApiException("forbidden", "admin required", HttpStatusCode.Forbidden)
    }
    val principalTenant = principal.tenantId
    if (!principalTenant.isNullOrBlank() && principalTenant != tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
    return AdminContext(
        tenantId = tenantId,
        correlationId = correlationId,
        actorId = principal.subject,
        accessRole = accessRole,
    )
}

private fun audit(
    auditTrailService: AuditTrailService,
    context: AdminContext,
    action: String,
) {
    val tenantId = context.tenantId
    val correlationId = context.correlationId
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

private fun auditMutation(
    auditTrailService: AuditTrailService,
    context: AdminContext,
    action: String,
    resourceType: String,
    resourceId: String,
    details: Map<String, String>,
) {
    auditTrailService.record(
        AuditEvent(
            actor = context.actorId,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            outcome = "success",
            requirementIds = listOf("REQ-ISO-006"),
            details =
                details + mapOf(
                    "tenantId" to context.tenantId,
                    "correlationId" to context.correlationId,
                ),
        ),
    )
}

private fun evidenceMutation(
    store: JdbcRegenOpsStore,
    context: AdminContext,
    action: String,
    resourceType: String,
    resourceId: String,
    details: Map<String, String>,
) {
    val payload =
        buildJsonObject {
            put("tenantId", context.tenantId)
            put("correlationId", context.correlationId)
            details.forEach { (key, value) -> put(key, value) }
        }
    store.appendEvidenceEvent(
        com.neogenesis.server.application.regenops.RegenEvidenceEvent(
            tenantId = context.tenantId,
            actionType = action,
            actorId = context.actorId,
            resourceType = resourceType,
            resourceId = resourceId,
            payloadJson = Json.encodeToString(payload),
            createdAtMs = Instant.now().toEpochMilli(),
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
                                role = normalizeRoleForResponse(rs.getString("role")),
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
    limit: Int,
): List<AdminTenantSummary> {
    val sql =
        if (context.accessRole == AdminAccessRole.ADMIN && context.tenantId.isBlank()) {
            """
            SELECT tenant_id, name, created_at
            FROM admin_tenants
            ORDER BY tenant_id ASC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT tenant_id, name, created_at
            FROM admin_tenants
            WHERE tenant_id = ?
            LIMIT ?
            """.trimIndent()
        }
    return dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            var index = 1
            if (context.accessRole != AdminAccessRole.ADMIN || context.tenantId.isNotBlank()) {
                statement.setString(index++, context.tenantId)
            }
            statement.setInt(index, limit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AdminTenantSummary(
                                id = rs.getString("tenant_id"),
                                name = rs.getString("name"),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun createTenant(
    dataSource: DataSource,
    tenantId: String,
    name: String,
): AdminTenantSummary {
    val now = Timestamp.from(Instant.now())
    dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO admin_tenants(tenant_id, name, created_at, updated_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setString(2, name)
            statement.setTimestamp(3, now)
            statement.setTimestamp(4, now)
            statement.executeUpdate()
        }
    }
    return AdminTenantSummary(id = tenantId, name = name, createdAt = now.toInstant().toString())
}

private fun listSites(
    dataSource: DataSource,
    tenantId: String,
    limit: Int,
): List<AdminSiteSummary> {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT tenant_id, site_id, name, created_at
            FROM admin_sites
            WHERE tenant_id = ?
            ORDER BY site_id ASC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setInt(2, limit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AdminSiteSummary(
                                id = rs.getString("site_id"),
                                name = rs.getString("name"),
                                tenantId = rs.getString("tenant_id"),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun createSite(
    dataSource: DataSource,
    tenantId: String,
    siteId: String,
    name: String,
): AdminSiteSummary {
    val now = Timestamp.from(Instant.now())
    dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO admin_sites(tenant_id, site_id, name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setString(2, siteId)
            statement.setString(3, name)
            statement.setTimestamp(4, now)
            statement.setTimestamp(5, now)
            statement.executeUpdate()
        }
    }
    return AdminSiteSummary(id = siteId, name = name, tenantId = tenantId, createdAt = now.toInstant().toString())
}

private fun listGatewayInventory(
    dataSource: DataSource,
    tenantId: String,
    limit: Int,
): List<AdminGatewaySummary> {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT tenant_id, gateway_id, display_name, status, last_heartbeat_at, updated_at, certificate_serial
            FROM regen_gateways
            WHERE tenant_id = ?
            ORDER BY updated_at DESC
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
                                id = rs.getString("gateway_id"),
                                name = rs.getString("display_name"),
                                tenantId = rs.getString("tenant_id"),
                                status = rs.getString("status"),
                                lastHeartbeatAt = rs.getTimestamp("last_heartbeat_at")?.toInstant()?.toString(),
                                updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
                                certificateSerial = rs.getString("certificate_serial") ?: "",
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun listFeatureFlags(
    dataSource: DataSource,
    tenantId: String,
): List<AdminFeatureFlagSummary> {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT tenant_id, flag_key, enabled, updated_at, updated_by
            FROM admin_feature_flags
            WHERE tenant_id = ?
            ORDER BY flag_key ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AdminFeatureFlagSummary(
                                key = rs.getString("flag_key"),
                                enabled = rs.getBoolean("enabled"),
                                updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
                                updatedBy = rs.getString("updated_by"),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun upsertFeatureFlag(
    dataSource: DataSource,
    tenantId: String,
    key: String,
    enabled: Boolean,
    actorId: String,
): AdminFeatureFlagSummary {
    val now = Timestamp.from(Instant.now())
    val updated =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE admin_feature_flags
                SET enabled = ?, updated_at = ?, updated_by = ?
                WHERE tenant_id = ? AND flag_key = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setBoolean(1, enabled)
                statement.setTimestamp(2, now)
                statement.setString(3, actorId)
                statement.setString(4, tenantId)
                statement.setString(5, key)
                statement.executeUpdate()
            }
        }
    if (updated == 0) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO admin_feature_flags(tenant_id, flag_key, enabled, updated_at, updated_by)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, key)
                statement.setBoolean(3, enabled)
                statement.setTimestamp(4, now)
                statement.setString(5, actorId)
                statement.executeUpdate()
            }
        }
    }
    return AdminFeatureFlagSummary(key = key, enabled = enabled, updatedAt = now.toInstant().toString(), updatedBy = actorId)
}

private fun createUser(
    dataSource: DataSource,
    username: String,
    passwordHash: String,
    role: String,
    tenantId: String,
): String {
    val existing =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM users WHERE username = ?").use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getString("id") else null }
            }
        }
    if (existing != null) {
        throw ApiException("user_exists", "user already exists", HttpStatusCode.Conflict)
    }
    val userId = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())
    dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO users(id, username, password_hash, role, tenant_id, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.setString(2, username)
            statement.setString(3, passwordHash)
            statement.setString(4, role)
            statement.setString(5, tenantId)
            statement.setBoolean(6, true)
            statement.setTimestamp(7, now)
            statement.setTimestamp(8, now)
            statement.executeUpdate()
        }
    }
    return userId
}

private fun updateUserRole(
    dataSource: DataSource,
    userId: String,
    role: String,
): AdminUserSummary {
    val now = Timestamp.from(Instant.now())
    val rows =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE users
                SET role = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, role)
                statement.setTimestamp(2, now)
                statement.setString(3, userId)
                statement.executeUpdate()
            }
        }
    if (rows == 0) {
        throw ApiException("user_missing", "user not found", HttpStatusCode.NotFound)
    }
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT id, username, role, tenant_id, is_active
            FROM users
            WHERE id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, userId)
            statement.executeQuery().use { rs ->
                if (rs.next()) {
                    AdminUserSummary(
                        id = rs.getString("id"),
                        username = rs.getString("username"),
                        role = normalizeRoleForResponse(rs.getString("role")),
                        tenantId = rs.getString("tenant_id"),
                        isActive = rs.getBoolean("is_active"),
                    )
                } else {
                    throw ApiException("user_missing", "user not found", HttpStatusCode.NotFound)
                }
            }
        }
    }
}

private fun normalizeRoleForStorage(role: String): String {
    return when (role.uppercase()) {
        "ADMIN" -> CanonicalRole.ADMIN.name
        "OPERATOR" -> CanonicalRole.OPERATOR.name
        "VIEWER" -> CanonicalRole.AUDITOR.name
        else -> throw ApiException("invalid_role", "role must be ADMIN, OPERATOR, or VIEWER", HttpStatusCode.BadRequest)
    }
}

private fun normalizeRoleForResponse(role: String): String {
    return when (role.uppercase()) {
        CanonicalRole.AUDITOR.name -> "VIEWER"
        else -> role
    }
}

private fun normalizeTenant(
    tenantId: String,
    context: AdminContext,
): String {
    val resolved = tenantId.trim().ifBlank { context.tenantId }
    if (resolved.isBlank()) {
        throw ApiException("tenant_required", "tenantId is required", HttpStatusCode.BadRequest)
    }
    if (context.tenantId.isNotBlank() && resolved != context.tenantId) {
        throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
    }
    return resolved
}

private fun resolveAdminRole(roles: Set<String>): AdminAccessRole {
    return when {
        roles.contains("ADMIN") || roles.contains("FOUNDER") -> AdminAccessRole.ADMIN
        roles.contains("OPERATOR") -> AdminAccessRole.OPERATOR
        roles.contains("VIEWER") || roles.contains("AUDITOR") -> AdminAccessRole.VIEWER
        else -> throw ApiException("forbidden", "admin access required", HttpStatusCode.Forbidden)
    }
}

data class AdminContext(
    val tenantId: String,
    val correlationId: String,
    val actorId: String,
    val accessRole: AdminAccessRole,
)

enum class AdminAccessRole {
    ADMIN,
    OPERATOR,
    VIEWER,
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
data class AdminUserCreateRequest(
    val username: String,
    val password: String,
    val role: String,
    val tenantId: String,
)

@Serializable
data class AdminUserRoleUpdateRequest(
    val role: String,
)

@Serializable
data class AdminUsersResponse(
    val users: List<AdminUserSummary>,
)

@Serializable
data class AdminTenantSummary(
    val id: String,
    val name: String,
    val createdAt: String,
)

@Serializable
data class AdminTenantsResponse(
    val tenants: List<AdminTenantSummary>,
)

@Serializable
data class AdminTenantCreateRequest(
    val tenantId: String,
    val name: String? = null,
)

@Serializable
data class AdminSiteSummary(
    val id: String,
    val name: String,
    val tenantId: String,
    val createdAt: String,
)

@Serializable
data class AdminSitesResponse(
    val sites: List<AdminSiteSummary>,
)

@Serializable
data class AdminSiteCreateRequest(
    val tenantId: String,
    val siteId: String,
    val name: String,
)

@Serializable
data class AdminGatewaySummary(
    val id: String,
    val name: String,
    val tenantId: String,
    val status: String,
    val lastHeartbeatAt: String?,
    val updatedAt: String,
    val certificateSerial: String,
)

@Serializable
data class AdminGatewayResponse(
    val gateways: List<AdminGatewaySummary>,
)

@Serializable
data class AdminFeatureFlagSummary(
    val key: String,
    val enabled: Boolean,
    val updatedAt: String,
    val updatedBy: String,
)

@Serializable
data class AdminFeatureFlagsResponse(
    val flags: List<AdminFeatureFlagSummary>,
)

@Serializable
data class AdminFeatureFlagUpsertRequest(
    val tenantId: String,
    val key: String,
    val enabled: Boolean,
)

@Serializable
data class ComplianceApprovalRequest(
    val reason: String? = null,
)

@Serializable
data class ComplianceApprovalDecisionRequest(
    val comment: String? = null,
)

@Serializable
data class ComplianceApprovalResponse(
    val id: String,
    val tenantId: String,
    val protocolId: String,
    val status: String,
    val requestedBy: String,
    val requestedAtMs: Long,
    val approvedBy: String?,
    val approvedAtMs: Long?,
    val approvalComment: String?,
    val consumedBy: String?,
    val consumedAtMs: Long?,
) {
    companion object {
        fun from(approval: ProtocolPublishApproval): ComplianceApprovalResponse {
            return ComplianceApprovalResponse(
                id = approval.id,
                tenantId = approval.tenantId,
                protocolId = approval.protocolId,
                status = approval.status,
                requestedBy = approval.requestedBy,
                requestedAtMs = approval.requestedAtMs,
                approvedBy = approval.approvedBy,
                approvedAtMs = approval.approvedAtMs,
                approvalComment = approval.approvalComment,
                consumedBy = approval.consumedBy,
                consumedAtMs = approval.consumedAtMs,
            )
        }
    }
}
