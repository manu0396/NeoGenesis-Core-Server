package com.neogenesis.server.infrastructure.security

import com.auth0.jwt.JWTVerifier
import com.neogenesis.server.application.security.AbacContext
import com.neogenesis.server.application.security.AbacDecision
import com.neogenesis.server.application.security.AbacPolicyEngine
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.TenantContext
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
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import kotlin.time.Duration.Companion.minutes

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

fun Application.configureTenantIsolation() {
    install(createApplicationPlugin("TenantIsolation") {
        onCall { call ->
            val principal = call.principal<NeoGenesisPrincipal>()
            if (principal != null) {
                val tenantId = principal.tenantId ?: "default"
                TenantContext.set(tenantId)
                org.slf4j.MDC.put("tenantId", tenantId)
            }
        }
    })
}

fun Application.configureRateLimiting(appConfig: AppConfig) {
    install(RateLimit) {
        register(RateLimitName("tenant-write")) {
            rateLimiter(limit = appConfig.rateLimits.telemetryWritesPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.tenantId() }
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = appConfig.rateLimits.authAttemptsPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.request.local.remoteAddress }
        }
    }
}

private class RoleAuthorizationConfig {
    var requiredRoles: Set<String> = emptySet()
    lateinit var metricsService: OperationalMetricsService
}

private class AbacAuthorizationConfig {
    lateinit var abacPolicyEngine: AbacPolicyEngine
    lateinit var metricsService: OperationalMetricsService
    lateinit var action: String
    lateinit var resourceType: String
    var getResourceId: (ApplicationCall) -> String? = { it.parameters["id"] }
    var getAttributes: (ApplicationCall) -> Map<String, Any> = { emptyMap() }
}

private val AbacAuthorizationPlugin =
    createRouteScopedPlugin(
        name = "AbacAuthorizationPlugin",
        createConfiguration = ::AbacAuthorizationConfig,
    ) {
        val policyEngine = pluginConfig.abacPolicyEngine
        val metricsService = pluginConfig.metricsService
        val action = pluginConfig.action
        val resourceType = pluginConfig.resourceType
        val getResourceId = pluginConfig.getResourceId
        val getAttributes = pluginConfig.getAttributes

        on(AuthenticationChecked) { call ->
            val principal = call.principal<NeoGenesisPrincipal>() ?: return@on
            
            val context = AbacContext(
                principal = principal,
                action = action,
                resourceType = resourceType,
                resourceId = getResourceId(call),
                attributes = getAttributes(call) + mapOf("tenant_id" to call.tenantId())
            )
            
            val decision = policyEngine.decide(context)
            metricsService.recordAbacDecision(action, decision.name)
            
            if (decision == AbacDecision.DENY) {
                throw SecurityPluginException(
                    status = HttpStatusCode.Forbidden,
                    code = "abac_denied",
                )
            }
        }
    }

fun Route.withAbac(
    action: String,
    resourceType: String,
    abacPolicyEngine: AbacPolicyEngine,
    metricsService: OperationalMetricsService,
    getResourceId: (ApplicationCall) -> String? = { it.parameters["id"] },
    getAttributes: (ApplicationCall) -> Map<String, Any> = { emptyMap() },
    build: Route.() -> Unit,
) {
    install(AbacAuthorizationPlugin) {
        this.abacPolicyEngine = abacPolicyEngine
        this.metricsService = metricsService
        this.action = action
        this.resourceType = resourceType
        this.getResourceId = getResourceId
        this.getAttributes = getAttributes
    }
    build()
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

fun ApplicationCall.tenantId(): String {
    return principal<NeoGenesisPrincipal>()?.tenantId ?: "default"
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
