package com.neogenesis.server.modules

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource

fun Route.healthModule(
    dataSource: DataSource,
    metrics: InMemoryMetrics,
    version: String,
    metricsPath: String,
) {
    val normalizedMetricsPath = if (metricsPath.startsWith('/')) metricsPath else "/$metricsPath"

    get("/health") {
        call.respond(
            HealthResponse(
                status = "ok",
                version = version,
                timestamp = Instant.now().toString(),
            ),
        )
    }

    get("/health/live") {
        call.respond(ProbeResponse(status = "live"))
    }

    get("/health/ready") {
        val dbOk = isDatabaseReachable(dataSource)
        val flywayOk = isMigrationHealthy(dataSource)
        val traceabilityOk = isTraceabilityLoaded()
        val ready = dbOk && flywayOk && traceabilityOk

        val payload =
            ReadyResponse(
                status = if (ready) "ready" else "not_ready",
                checks =
                    mapOf(
                        "database_reachable" to dbOk,
                        "migrations_ok" to flywayOk,
                        "traceability_loaded" to traceabilityOk,
                    ),
            )
        if (ready) {
            call.respond(HttpStatusCode.OK, payload)
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, payload)
        }
    }

    get("/ready") {
        val dbOk = isDatabaseReachable(dataSource)
        val flywayOk = isMigrationHealthy(dataSource)
        val traceabilityOk = isTraceabilityLoaded()
        val ready = dbOk && flywayOk && traceabilityOk

        val payload =
            ReadyResponse(
                status = if (ready) "ready" else "not_ready",
                checks =
                    mapOf(
                        "database_reachable" to dbOk,
                        "migrations_ok" to flywayOk,
                        "traceability_loaded" to traceabilityOk,
                    ),
            )
        if (ready) {
            call.respond(HttpStatusCode.OK, payload)
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, payload)
        }
    }

    get(normalizedMetricsPath) {
        metrics.increment("metrics_scrape_total")
        call.respondText(metrics.renderPrometheus())
    }
}

private fun isDatabaseReachable(dataSource: DataSource): Boolean {
    return runCatching {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SELECT 1")
            }
        }
        true
    }.getOrDefault(false)
}

private fun isMigrationHealthy(dataSource: DataSource): Boolean {
    return runCatching {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT success
                FROM flyway_schema_history
                ORDER BY installed_rank DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    rs.next() && rs.getBoolean("success")
                }
            }
        }
    }.recover {
        if (it is SQLException) {
            false
        } else {
            throw it
        }
    }.getOrDefault(false)
}

private fun isTraceabilityLoaded(): Boolean {
    val resource = object {}.javaClass.classLoader.getResourceAsStream("iso13485/traceability.csv") ?: return false
    return resource.bufferedReader().useLines { lines ->
        lines.drop(1).any { it.isNotBlank() }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: String,
)

@Serializable
data class ProbeResponse(
    val status: String,
)

@Serializable
data class ReadyResponse(
    val status: String,
    val checks: Map<String, Boolean>,
)
