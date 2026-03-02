package com.neogenesis.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceTierEnforcementTest {
    @Test
    fun `tier2 cannot mutate and tier3 is read-only`() =
        testApplication {
            environment { config = testConfig() }
            application { module() }

            val token = issueToken(roles = listOf("CONTROLLER"), tenantId = "tenant-a")

            val tier2Denied =
                client.post("/devices") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    addDeviceHeaders(tier = "TIER_2")
                    contentType(ContentType.Application.Json)
                    setBody("""{"id":"dev-t2","name":"Device 2","tenantId":"tenant-a"}""")
                }
            assertEquals(HttpStatusCode.Forbidden, tier2Denied.status)

            val tier3Read =
                client.get("/print-sessions/active") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    addDeviceHeaders(tier = "TIER_3")
                }
            assertEquals(HttpStatusCode.OK, tier3Read.status)

            val tier3Denied =
                client.get("/audit-bundle/job/job-x.zip") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    addDeviceHeaders(tier = "TIER_3")
                    header("X-Correlation-Id", "corr-tier3")
                }
            assertEquals(HttpStatusCode.Forbidden, tier3Denied.status)
        }

    private fun issueToken(roles: List<String>, tenantId: String): String {
        return JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject("test-user")
            .withClaim("roles", roles)
            .withClaim("tenantId", tenantId)
            .sign(Algorithm.HMAC256(TEST_SECRET))
    }

    private fun testConfig(): MapApplicationConfig {
        val dbUrl = "jdbc:h2:mem:device-tier-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        return MapApplicationConfig(
            "neogenesis.runtime.environment" to "test",
            "neogenesis.database.jdbcUrl" to dbUrl,
            "neogenesis.database.username" to "sa",
            "neogenesis.database.password" to "",
            "neogenesis.database.maximumPoolSize" to "2",
            "neogenesis.database.migrateOnStartup" to "true",
            "neogenesis.security.jwt.secret" to TEST_SECRET,
            "neogenesis.security.jwt.issuer" to TEST_ISSUER,
            "neogenesis.security.jwt.audience" to TEST_AUDIENCE,
            "neogenesis.security.jwt.realm" to "NeoGenesis",
            "neogenesis.adminBootstrap.enabled" to "true",
            "neogenesis.adminBootstrap.user" to "admin",
            "neogenesis.adminBootstrap.password" to "admin-password",
            "neogenesis.audit.bundle.mode" to "true",
        )
    }

    companion object {
        private const val TEST_ISSUER = "integration-test-issuer"
        private const val TEST_AUDIENCE = "integration-test-audience"
        private const val TEST_SECRET = "integration-test-secret-12345678901234567890"
    }
}
