package com.neogenesis.server.infrastructure.security

import com.auth0.jwt.JWTVerifier
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

class SecurityPluginException(
    val status: HttpStatusCode,
    val code: String
) : RuntimeException(code)

fun Application.configureAuthentication(
    config: AppConfig.SecurityConfig.JwtConfig,
    verifier: JWTVerifier
) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(verifier)
            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null

                val claim = credential.payload.getClaim("roles")
                val roles = claim.asList(String::class.java)
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: claim.asString()
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        .orEmpty()

                NeoGenesisPrincipal(
                    subject = subject,
                    roles = roles,
                    clientId = credential.payload.getClaim("client_id").asString()
                )
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, SecurityError("unauthorized"))
            }
        }
    }
}

fun Application.configureHttpProxyMutualTlsValidation(config: AppConfig.SecurityConfig.MtlsConfig.HttpProxyValidationConfig) {
    if (!config.enabled) {
        return
    }

    install(
        createApplicationPlugin(name = "HttpProxyMutualTlsValidation") {
            onCall { call ->
                val path = call.request.path()
                val isPublicPath = path.startsWith("/health") || path == "/metrics"
                if (isPublicPath) {
                    return@onCall
                }

                val status = call.request.headers[config.verifyHeaderName]
                if (status != config.verifySuccessValue) {
                    throw SecurityPluginException(
                        status = HttpStatusCode.Forbidden,
                        code = "mtls_validation_failed"
                    )
                }
            }
        }
    )
}

private class RoleAuthorizationConfig {
    var requiredRoles: Set<String> = emptySet()
    lateinit var metricsService: OperationalMetricsService
}

private val RoleAuthorizationPlugin = createRouteScopedPlugin(
    name = "RoleAuthorizationPlugin",
    createConfiguration = ::RoleAuthorizationConfig
) {
    val requiredRoles = pluginConfig.requiredRoles
    val metricsService = pluginConfig.metricsService

    on(AuthenticationChecked) { call ->
        val principal = call.principal<NeoGenesisPrincipal>()
        if (principal == null) {
            throw SecurityPluginException(
                status = HttpStatusCode.Unauthorized,
                code = "unauthorized"
            )
        }

        if (requiredRoles.isNotEmpty() && principal.roles.intersect(requiredRoles).isEmpty()) {
            metricsService.recordAuthzDenied()
            throw SecurityPluginException(
                status = HttpStatusCode.Forbidden,
                code = "forbidden"
            )
        }
    }
}

fun Route.secured(
    requiredRoles: Set<String>,
    metricsService: OperationalMetricsService,
    build: Route.() -> Unit
) {
    authenticate("auth-jwt") {
        install(RoleAuthorizationPlugin) {
            this.requiredRoles = requiredRoles
            this.metricsService = metricsService
        }
        build()
    }
}

fun ApplicationCall.actor(): String {
    return principal<NeoGenesisPrincipal>()?.subject ?: "system"
}

@Serializable
private data class SecurityError(val error: String)
