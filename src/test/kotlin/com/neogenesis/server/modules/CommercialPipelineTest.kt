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
import kotlin.test.assertTrue

class CommercialPipelineTest {
    @Test
    fun `opportunity loi export csv`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val token = loginAndGetToken()
            val correlationId = "corr-123"

            val accountResponse =
                client.post("/commercial/accounts") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "tenantId":"tenant-a",
                          "correlationId":"$correlationId",
                          "name":"Acme Bio"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, accountResponse.status)
            val accountId = Json.parseToJsonElement(accountResponse.body<String>()).jsonObject["id"]!!.jsonPrimitive.content

            val oppResponse =
                client.post("/commercial/opportunities") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "tenantId":"tenant-a",
                          "correlationId":"$correlationId",
                          "accountId":"$accountId",
                          "stage":"Lead",
                          "expectedValueEur":100000,
                          "probability":0.3,
                          "owner":"founder",
                          "notes":"seed round"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, oppResponse.status)
            val opportunityId = Json.parseToJsonElement(oppResponse.body<String>()).jsonObject["id"]!!.jsonPrimitive.content

            val loiResponse =
                client.post("/commercial/lois") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "tenantId":"tenant-a",
                          "correlationId":"$correlationId",
                          "opportunityId":"$opportunityId",
                          "status":"draft",
                          "amountRange":"€100k-€250k",
                          "attachmentRef":"s3://loi/acme.pdf"
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, loiResponse.status)

            val export =
                client.get("/commercial/pipeline/export?tenant_id=tenant-a") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("X-Correlation-Id", correlationId)
                }
            assertEquals(HttpStatusCode.OK, export.status)
            val csv = export.body<String>()
            assertTrue(csv.contains(opportunityId))
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
        val dbUrl = "jdbc:h2:mem:commercial-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
            "neogenesis.commercial.mode" to "true",
        )
    }
}
