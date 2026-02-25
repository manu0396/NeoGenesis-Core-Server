package com.neogenesis.metrics

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsServiceTest {
    @Test
    fun `metrics score requires headers`() =
        testApplication {
            application {
                metricsModule(
                    MetricsConfig(
                        enabled = true,
                        port = 0,
                        storageDir = "build/tmp/test-metrics-service",
                    ),
                )
            }

            val missing =
                client.get("/metrics/score")
            assertEquals(HttpStatusCode.BadRequest, missing.status)

            val ok =
                client.get("/metrics/score") {
                    header("X-Tenant-Id", "tenant-a")
                    header("X-Actor-Id", "actor-1")
                    header("X-Correlation-Id", "corr-1")
                }
            assertEquals(HttpStatusCode.OK, ok.status)
        }
}
