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
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidencePackModuleTest {
    @Test
    fun `evidence pack requires correlation and tenant`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val token = loginAndGetToken()
            client.post("/devices") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"dev-e1","name":"Device 1","tenantId":"tenant-a"}""")
            }
            client.post("/jobs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"job-e1","deviceId":"dev-e1","tenantId":"tenant-a"}""")
            }

            val missingTenant =
                client.get("/evidence-pack/job/job-e1/report.csv") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", "corr-1")
                }
            assertEquals(HttpStatusCode.BadRequest, missingTenant.status)

            val ok =
                client.get("/evidence-pack/job/job-e1/report.csv?tenant_id=tenant-a") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", "corr-1")
                }
            assertEquals(HttpStatusCode.OK, ok.status)
        }

    @Test
    fun `audit bundle requires correlation and tenant`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val token = loginAndGetToken()
            client.post("/devices") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"dev-e2","name":"Device 2","tenantId":"tenant-a"}""")
            }
            client.post("/jobs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"job-e2","deviceId":"dev-e2","tenantId":"tenant-a"}""")
            }

            val missingTenant =
                client.get("/audit-bundle/job/job-e2.zip") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", "corr-2")
                }
            assertEquals(HttpStatusCode.BadRequest, missingTenant.status)

            val ok =
                client.get("/audit-bundle/job/job-e2.zip?tenant_id=tenant-a") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", "corr-2")
                }
            assertEquals(HttpStatusCode.OK, ok.status)
        }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.loginAndGetToken(): String {
        val response =
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"username":"admin","password":"admin-password"}""")
            }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.body<String>())
            .jsonObject["accessToken"]!!
            .jsonPrimitive.content
    }

    private fun testConfig(): MapApplicationConfig {
        val dbUrl = "jdbc:h2:mem:evidence-pack-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
            "neogenesis.evidence.pack.mode" to "true",
            "neogenesis.audit.bundle.mode" to "true",
        )
    }
}
