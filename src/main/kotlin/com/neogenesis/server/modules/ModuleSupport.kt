package com.neogenesis.server.modules

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.UserRecord
import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ApiException(
    val code: String,
    override val message: String,
    val status: io.ktor.http.HttpStatusCode,
) : RuntimeException(message)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val traceId: String,
)

class AuthTokenIssuer(val config: AppConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwt.secret)

    fun issue(user: UserRecord): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(config.jwt.ttlSeconds)
        return JWT.create()
            .withIssuer(config.jwt.issuer)
            .withAudience(config.jwt.audience)
            .withSubject(user.id)
            .withClaim("username", user.username)
            .withClaim("role", user.role.name)
            .withClaim("roles", listOf(user.role.name))
            .withClaim("tenantId", user.tenantId)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }
}

class PasswordService {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))

    fun matches(
        raw: String,
        hash: String,
    ): Boolean = runCatching { BCrypt.checkpw(raw, hash) }.getOrDefault(false)
}

class BruteForceLimiter(
    private val maxAttemptsPerMinute: Int,
) {
    private val byIp = ConcurrentHashMap<String, MutableList<Long>>()
    private val byUser = ConcurrentHashMap<String, MutableList<Long>>()
    private val windowMs = 60_000L

    fun isBlocked(
        ip: String,
        username: String,
    ): Boolean {
        val now = System.currentTimeMillis()
        val ipAttempts = pruneAndCount(byIp, ip, now)
        val userAttempts = pruneAndCount(byUser, username.lowercase(), now)
        return ipAttempts >= maxAttemptsPerMinute || userAttempts >= maxAttemptsPerMinute
    }

    fun registerFailure(
        ip: String,
        username: String,
    ) {
        val now = System.currentTimeMillis()
        register(byIp, ip, now)
        register(byUser, username.lowercase(), now)
    }

    fun clear(
        ip: String,
        username: String,
    ) {
        byIp.remove(ip)
        byUser.remove(username.lowercase())
    }

    private fun register(
        store: ConcurrentHashMap<String, MutableList<Long>>,
        key: String,
        now: Long,
    ) {
        val attempts = store.computeIfAbsent(key) { mutableListOf() }
        synchronized(attempts) {
            attempts.add(now)
            attempts.removeIf { now - it > windowMs }
        }
    }

    private fun pruneAndCount(
        store: ConcurrentHashMap<String, MutableList<Long>>,
        key: String,
        now: Long,
    ): Int {
        val attempts = store[key] ?: return 0
        synchronized(attempts) {
            attempts.removeIf { now - it > windowMs }
            return attempts.size
        }
    }
}

class InMemoryMetrics {
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val counters = ConcurrentHashMap<String, Counter>()

    fun increment(name: String) {
        counters.computeIfAbsent(name) { Counter.builder(name).register(registry) }
            .increment()
    }

    fun renderPrometheus(): String {
        return registry.scrape()
    }

    fun registry(): PrometheusMeterRegistry = registry
}

fun shouldBootstrapAdmin(config: AppConfig): Boolean {
    if (config.adminBootstrap.enabled) {
        return true
    }
    val env = config.env.lowercase()
    return env == "local" || env == "dev" || env == "development"
}

fun bootstrapUserId(): String = UUID.randomUUID().toString()

fun parseRole(role: String): CanonicalRole {
    return runCatching { CanonicalRole.valueOf(role.trim().uppercase()) }
        .getOrElse { throw ApiException("invalid_role", "Unsupported role: $role", io.ktor.http.HttpStatusCode.BadRequest) }
}
