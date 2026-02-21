package com.neogenesis.server.infrastructure.security

import io.ktor.server.auth.Principal

data class NeoGenesisPrincipal(
    val subject: String,
    val roles: Set<String>,
    val clientId: String?
) : Principal
