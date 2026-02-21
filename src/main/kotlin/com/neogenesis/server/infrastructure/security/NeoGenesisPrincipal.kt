package com.neogenesis.server.infrastructure.security

data class NeoGenesisPrincipal(
    val subject: String,
    val roles: Set<String>,
    val clientId: String?
)
