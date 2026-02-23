package com.neogenesis.server.infrastructure.security

import com.auth0.jwt.JWTVerifier
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
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
import io.ktor.server.routing.Route

class SecurityPluginException(
    val status: HttpStatusCode,
    val code: String,
) : RuntimeException(code)

fun Application.configureAuthentication(
    config: AppConfig.SecurityConfig.JwtConfig,
    verifier: JWTVerifier,
) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm
            verifier(verifier)
            validate { credential ->
                val subject = credential.payload.subject ?: return@validate null

                val claim = credential.payload.getClaim("roles")
                val claimRoles =
                    claim.asList(String::class.java)
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        ?: claim.asString()
                            ?.split(',')
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            .orEmpty()
                val roleClaim =
                    credential.payload.getClaim("role").asString()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                val normalizedRoles =
                    (claimRoles + listOfNotNull(roleClaim))
                        .map { it.uppercase() }
                        .toSet()

                NeoGenesisPrincipal(
                    subject = subject,
                    roles = normalizedRoles,
                    clientId = credential.payload.getClaim("client_id").asString(),
                    username = credential.payload.getClaim("username").asString() ?: subject,
                    role = roleClaim?.uppercase(),
                    tenantId = credential.payload.getClaim("tenantId").asString(),
                )
            }
            challenge { _, _ ->
                throw SecurityPluginException(
                    status = HttpStatusCode.Unauthorized,
                    code = "unauthorized",
                )
            }
        }
    }
}

fun Application.configureHttpProxyMutualTlsValidation(
    config: AppConfig.SecurityConfig.MtlsConfig.HttpProxyValidationConfig,
    metricsPath: String,
) {
    if (!config.enabled) {
        return
    }

    val normalizedMetricsPath = if (metricsPath.startsWith('/')) metricsPath else "/$metricsPath"
    install(
        createApplicationPlugin(name = "HttpProxyMutualTlsValidation") {
            onCall { call ->
                val path = call.request.path()
                val isPublicPath = path.startsWith("/health") || path == normalizedMetricsPath
                if (isPublicPath) {
                    return@onCall
                }

                val status = call.request.headers[config.verifyHeaderName]
                if (status != config.verifySuccessValue) {
                    throw SecurityPluginException(
                        status = HttpStatusCode.Forbidden,
                        code = "mtls_validation_failed",
                    )
                }
            }
        },
    )
}

private class RoleAuthorizationConfig {
    var requiredRoles: Set<String> = emptySet()
    lateinit var metricsService: OperationalMetricsService
}

private val RoleAuthorizationPlugin =
    createRouteScopedPlugin(
        name = "RoleAuthorizationPlugin",
        createConfiguration = ::RoleAuthorizationConfig,
    ) {
        val requiredRoles = pluginConfig.requiredRoles.map { it.uppercase() }.toSet()
        val metricsService = pluginConfig.metricsService

        on(AuthenticationChecked) { call ->
            val principal = call.principal<NeoGenesisPrincipal>()
            if (principal == null) {
                throw SecurityPluginException(
                    status = HttpStatusCode.Unauthorized,
                    code = "unauthorized",
                )
            }

            if (requiredRoles.isNotEmpty() && principal.roles.map { it.uppercase() }.toSet().intersect(requiredRoles).isEmpty()) {
                metricsService.recordAuthzDenied()
                throw SecurityPluginException(
                    status = HttpStatusCode.Forbidden,
                    code = "forbidden",
                )
            }
        }
    }

fun Route.secured(
    requiredRoles: Set<String>,
    metricsService: OperationalMetricsService,
    build: Route.() -> Unit,
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

fun ApplicationCall.requireRole(vararg requiredRoles: CanonicalRole): NeoGenesisPrincipal {
    val principal =
        principal<NeoGenesisPrincipal>()
            ?: throw SecurityPluginException(HttpStatusCode.Unauthorized, "unauthorized")
    if (requiredRoles.isEmpty()) {
        return principal
    }
    val expected = requiredRoles.map { it.name }.toSet()
    if (principal.roles.intersect(expected).isEmpty()) {
        throw SecurityPluginException(HttpStatusCode.Forbidden, "forbidden")
    }
    return principal
}

fun ApplicationCall.enforceRole(vararg requiredRoles: CanonicalRole): NeoGenesisPrincipal = requireRole(*requiredRoles)
