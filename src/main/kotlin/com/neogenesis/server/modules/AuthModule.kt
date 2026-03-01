package com.neogenesis.server.modules

import com.neogenesis.server.infrastructure.persistence.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

fun Route.authModule(
    userRepository: UserRepository,
    passwordService: PasswordService,
    tokenIssuer: AuthTokenIssuer,
    bruteForceLimiter: BruteForceLimiter,
    metrics: InMemoryMetrics,
) {
    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val normalizedUsername = request.username.trim()
        if (normalizedUsername.isEmpty() || request.password.isBlank()) {
            throw ApiException("invalid_credentials", "Invalid username or password", HttpStatusCode.Unauthorized)
        }

        val ip =
            call.request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()
                ?: call.request.headers["X-Real-IP"]?.trim()
                ?: "unknown"
        if (bruteForceLimiter.isBlocked(ip, normalizedUsername)) {
            metrics.increment("auth_bruteforce_block_total")
            throw ApiException("too_many_attempts", "Too many login attempts", HttpStatusCode.TooManyRequests)
        }

        val user =
            userRepository.findByUsername(normalizedUsername)
                ?.takeIf { it.isActive }
                ?: run {
                    bruteForceLimiter.registerFailure(ip, normalizedUsername)
                    metrics.increment("auth_login_failure_total")
                    throw ApiException("invalid_credentials", "Invalid username or password", HttpStatusCode.Unauthorized)
                }

        if (!passwordService.matches(request.password, user.passwordHash)) {
            bruteForceLimiter.registerFailure(ip, normalizedUsername)
            metrics.increment("auth_login_failure_total")
            throw ApiException("invalid_credentials", "Invalid username or password", HttpStatusCode.Unauthorized)
        }

        bruteForceLimiter.clear(ip, normalizedUsername)
        metrics.increment("auth_login_success_total")
        call.respond(
            HttpStatusCode.OK,
            LoginResponse(
                accessToken = tokenIssuer.issue(user),
                tokenType = "Bearer",
                expiresInSeconds = tokenIssuer.config.jwt.ttlSeconds,
                role = user.role.name,
                username = user.username,
                tenantId = user.tenantId,
            ),
        )
    }
}

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val role: String,
    val username: String,
    val tenantId: String?,
)
