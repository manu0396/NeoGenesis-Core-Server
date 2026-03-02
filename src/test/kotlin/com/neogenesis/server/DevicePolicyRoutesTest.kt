package com.neogenesis.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevicePolicyRoutesTest {
    @Test
    fun `device policy endpoints return policy`() =
        testApplication {
            environment {
                config = testConfig()
            }
            application {
                module()
            }

            val response = client.get("/api/v1/device-policy")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = Json.parseToJsonElement(response.body<String>()).jsonObject
            assertTrue(json["version"]!!.jsonPrimitive.int >= 1)

            val register =
                client.post("/api/v1/device/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "deviceId":"test-device-1",
                          "deviceClass":"WINDOWS_DESKTOP",
                          "tier":"TIER_1",
                          "appVersion":"1.0.0",
                          "platform":"desktop",
                          "model":"test",
                          "osVersion":"test-os",
                          "policyVersion":1
                        }
                        """.trimIndent(),
                    )
                }
            assertEquals(HttpStatusCode.OK, register.status)
            val regJson = Json.parseToJsonElement(register.body<String>()).jsonObject
            assertTrue(regJson["version"]!!.jsonPrimitive.int >= 1)
        }

    private fun testConfig(): MapApplicationConfig {
        val dbUrl = "jdbc:h2:mem:device-policy-${System.nanoTime()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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


