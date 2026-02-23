package com.neogenesis.server.infrastructure.security

data class NeoGenesisPrincipal(
    val subject: String,
    val roles: Set<String>,
    val clientId: String?,
    val username: String = subject,
    val role: String? = null,
    val tenantId: String? = null,
)
