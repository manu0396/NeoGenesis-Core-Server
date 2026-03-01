package com.neogenesis.server.modules

import com.neogenesis.server.module
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoUiModuleTest {
    @Test
    fun `demo metrics and commercial endpoints return data`() =
        testApplication {
            environment { config = testConfig() }
            application { module() }

            val token = loginAndGetToken()
            val correlationId = "corr-demo-1"

            val score =
                client.get("/api/v1/metrics/reproducibility-score?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, score.status)

            val alerts =
                client.get("/api/v1/metrics/drift-alerts?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, alerts.status)

            val pipeline =
                client.get("/api/v1/commercial/pipeline?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, pipeline.status)

            val export =
                client.get("/api/v1/commercial/pipeline/export?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, export.status)
            assertTrue(export.body<String>().contains("expectedRevenueEur"))
        }

    @Test
    fun `demo simulator and export endpoints return payloads`() =
        testApplication {
            environment { config = testConfig() }
            application { module() }

            val token = loginAndGetToken()
            val correlationId = "corr-demo-2"

            val simResponse =
                client.post("/demo/simulator/runs?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "protocolId":"sim-protocol",
                          "samples":25,
                          "intervalMs":200
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, simResponse.status)
            val runId =
                Json.parseToJsonElement(simResponse.body<String>())
                    .jsonObject["runId"]!!
                    .jsonPrimitive
                    .content

            val events =
                client.get("/demo/simulator/runs/$runId/events?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, events.status)

            val telemetryExport =
                client.get("/api/v1/telemetry/$runId/export?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                    header(HttpHeaders.Accept, "text/csv")
                }
            assertEquals(HttpStatusCode.OK, telemetryExport.status)
            val csv = telemetryExport.body<String>()
            assertTrue(csv.contains("metric_key"))

            val evidenceBundle =
                client.get("/api/v1/evidence/$runId/package?tenant_id=tenant-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, evidenceBundle.status)
            val bytes = evidenceBundle.body<ByteArray>()
            val names = mutableSetOf<String>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    names.add(entry.name)
                    entry = zip.nextEntry
                }
            }
            assertTrue(names.contains("manifest.json"))
        }

    private suspend fun ApplicationTestBuilder.loginAndGetToken(): String {
        val response =
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"admin","password":"admin-password"}""")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.body<String>())
            .jsonObject["accessToken"]!!
            .jsonPrimitive
            .content
    }

    private fun testConfig(): MapApplicationConfig {
        val dbUrl = "jdbc:h2:mem:demo-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        return MapApplicationConfig(
            "neogenesis.runtime.environment" to "test",
            "neogenesis.database.jdbcUrl" to dbUrl,
            "neogenesis.database.username" to "sa",
            "neogenesis.database.password" to "",
            "neogenesis.database.maximumPoolSize" to "2",
            "neogenesis.database.migrateOnStartup" to "true",
            "neogenesis.security.jwt.secret" to "integration-test-secret-12345678901234567890",
            "neogenesis.security.jwt.issuer" to "integration-test-issuer",
            "neogenesis.security.jwt.audience" to "integration-test-audience",
            "neogenesis.security.jwt.realm" to "NeoGenesis",
            "neogenesis.adminBootstrap.enabled" to "true",
            "neogenesis.adminBootstrap.user" to "admin",
            "neogenesis.adminBootstrap.password" to "admin-password",
            "neogenesis.demo.mode" to "true",
        )
    }
}
