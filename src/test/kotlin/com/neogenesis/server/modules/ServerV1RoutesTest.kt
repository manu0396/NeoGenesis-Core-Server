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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerV1RoutesTest {
    @Test
    fun `auth login success and failure`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val success =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"admin-password"}""")
                }
            assertEquals(HttpStatusCode.OK, success.status)
            val successJson = Json.parseToJsonElement(success.body<String>()).jsonObject
            assertTrue(successJson["accessToken"]!!.jsonPrimitive.content.isNotBlank())

            val failure =
                client.post("/auth/login") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"username":"admin","password":"bad-password"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, failure.status)
        }

    @Test
    fun `unauthorized access is blocked`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val response = client.get("/jobs")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `telemetry ingestion works`() =
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
                setBody("""{"id":"dev-1","name":"Device 1","tenantId":"tenant-a"}""")
            }.also { assertEquals(HttpStatusCode.Created, it.status) }

            client.post("/jobs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"job-1","deviceId":"dev-1","tenantId":"tenant-a"}""")
            }.also { assertEquals(HttpStatusCode.Created, it.status) }

            val ingest =
                client.post("/telemetry/job/job-1") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"deviceId":"dev-1","payload":{"temp":36.5,"pressure":111}}""")
                }
            assertEquals(HttpStatusCode.Accepted, ingest.status)

            val read =
                client.get("/telemetry/job/job-1?limit=10") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, read.status)
            val array = Json.parseToJsonElement(read.body<String>()).jsonArray
            assertTrue(array.isNotEmpty())
        }

    @Test
    fun `audit chain verification works for sequence`() =
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
                setBody("""{"id":"dev-2","name":"Device 2","tenantId":"tenant-b"}""")
            }.also { assertEquals(HttpStatusCode.Created, it.status) }

            client.post("/jobs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"job-2","deviceId":"dev-2","tenantId":"tenant-b"}""")
            }.also { assertEquals(HttpStatusCode.Created, it.status) }

            client.post("/telemetry/job/job-2") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"deviceId":"dev-2","payload":{"viability":0.94}}""")
            }.also { assertEquals(HttpStatusCode.Accepted, it.status) }

            client.post("/twin/job/job-2") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody("""{"deviceId":"dev-2","payload":{"risk":0.03}}""")
            }.also { assertEquals(HttpStatusCode.Accepted, it.status) }

            val auditResponse =
                client.get("/audit/job/job-2") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, auditResponse.status)
            val verification =
                Json.parseToJsonElement(auditResponse.body<String>())
                    .jsonObject["verification"]!!
                    .jsonObject
            assertTrue(verification["valid"]!!.jsonPrimitive.boolean)
        }

    @Test
    fun `billing status returns entitlements when disabled`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val token = loginAndGetToken()
            val response =
                client.get("/billing/status") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            assertEquals(HttpStatusCode.OK, response.status)
            val payload = Json.parseToJsonElement(response.body<String>()).jsonObject
            val entitlements = payload["entitlements"]!!.jsonArray
            assertTrue(entitlements.isNotEmpty())
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
        val dbUrl = "jdbc:h2:mem:v1-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
        )
    }
}
