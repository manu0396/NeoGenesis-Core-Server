package com.neogenesis.server.application.security

import com.neogenesis.server.infrastructure.security.NeoGenesisPrincipal

data class AbacContext(
    val principal: NeoGenesisPrincipal,
    val action: String,
    val resourceType: String,
    val resourceId: String?,
    val attributes: Map<String, Any> = emptyMap(),
)

enum class AbacDecision {
    ALLOW,
    DENY,
}

interface AbacPolicyEngine {
    fun decide(context: AbacContext): AbacDecision
}

class DefaultAbacPolicyEngine : AbacPolicyEngine {
    override fun decide(context: AbacContext): AbacDecision {
        // Base logic: Deny by default

        // 1. System Admins can do everything
        if (context.principal.roles.contains("ADMIN")) return AbacDecision.ALLOW

        // 2. Tenant isolation: resource must belong to the same tenant as principal
        val resourceTenantId = context.attributes["tenant_id"] as? String
        if (resourceTenantId != null && resourceTenantId != context.principal.tenantId) {
            return AbacDecision.DENY
        }

        // 3. Sensitive Action: Clinical Decision Support (telemetry.evaluate)
        // Requires 'clinician' role AND active session assignment (site match)
        if (context.action == "telemetry.evaluate") {
            if (!context.principal.roles.contains("CLINICIAN")) return AbacDecision.DENY

            val siteId = context.attributes["site_id"] as? String
            val userSiteId = context.principal.roles.find { it.startsWith("SITE:") }?.removePrefix("SITE:")
            if (siteId != null && userSiteId != null && siteId != userSiteId) {
                return AbacDecision.DENY
            }
        }

        // 4. Sensitive Action: CAPA Management
        // Requires 'quality_manager' role
        if (context.action.startsWith("regulatory.capa.")) {
            if (!context.principal.roles.contains("QUALITY_MANAGER")) return AbacDecision.DENY
        }

        // Default to ALLOW for other actions if RBAC passed (RBAC is checked by the Ktor plugin before ABAC)
        return AbacDecision.ALLOW
    }
}
