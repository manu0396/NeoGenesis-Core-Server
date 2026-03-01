package com.neogenesis.server.infrastructure.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityPluginsTest {
    @Test
    fun `returns 401 when missing JWT`() =
        testApplication {
            application { configureSecuredTestApp() }

            val response = client.get("/secure")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `returns 403 when role is insufficient`() =
        testApplication {
            application { configureSecuredTestApp() }

            val token = issueToken(roles = listOf("viewer"))
            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `returns 200 when role is authorized`() =
        testApplication {
            application { configureSecuredTestApp() }

            val token = issueToken(roles = listOf("controller"))
            val response =
                client.get("/secure") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    private fun io.ktor.server.application.Application.configureSecuredTestApp() {
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            exception<SecurityPluginException> { call, cause ->
                call.respond(cause.status, ApiError(cause.code))
            }
        }
        configureAuthentication(
            config =
                AppConfig.SecurityConfig.JwtConfig(
                    realm = TEST_REALM,
                    issuer = TEST_ISSUER,
                    audience = TEST_AUDIENCE,
                    secret = TEST_SECRET,
                    mode = "hmac",
                    jwksUrl = null,
                ),
            verifier = verifier(),
        )
        routing {
            secured(
                requiredRoles = setOf("controller"),
                metricsService = OperationalMetricsService(SimpleMeterRegistry()),
            ) {
                get("/secure") {
                    call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
                }
            }
        }
    }

    private fun issueToken(roles: List<String>): String {
        return JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject("test-user")
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(TEST_SECRET))
    }

    private fun verifier() =
        JWT.require(Algorithm.HMAC256(TEST_SECRET))
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .build()

    companion object {
        private const val TEST_REALM = "NeoGenesis"
        private const val TEST_ISSUER = "neogenesis-auth"
        private const val TEST_AUDIENCE = "neogenesis-api"
        private const val TEST_SECRET = "integration-test-secret-with-at-least-32-chars"
    }
}

@Serializable
private data class ApiError(
    val error: String,
)
