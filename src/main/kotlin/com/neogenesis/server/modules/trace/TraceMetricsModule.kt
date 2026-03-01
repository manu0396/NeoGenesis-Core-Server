package com.neogenesis.server.modules.trace

import com.neogenesis.server.application.regenops.RegenDriftAlert
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenOpsStore
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

fun Route.traceMetricsModule(
    dataSource: DataSource,
    regenOpsService: RegenOpsService,
    regenOpsStore: RegenOpsStore,
) {
    authenticate("auth-jwt") {
        get("/metrics/score") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            val runId = call.request.queryParameters["run_id"]?.trim().orEmpty()
            val baselineWindow = call.request.queryParameters["baseline_n"]?.toIntOrNull() ?: 5
            val metricKey = call.request.queryParameters["metric_key"]?.trim().orEmpty().ifBlank { "pressure_kpa" }
            val zThreshold = call.request.queryParameters["z_threshold"]?.toDoubleOrNull() ?: 3.0

            val resolvedRunId = if (runId.isBlank()) latestRunId(dataSource, tenantId) else runId
            if (resolvedRunId.isBlank()) {
                throw ApiException("run_missing", "No runs found for tenant", HttpStatusCode.NotFound)
            }

            val currentScore = regenOpsService.getReproducibilityScore(tenantId, resolvedRunId).score
            val baselineRuns = listBaselineRuns(dataSource, tenantId, resolvedRunId, baselineWindow)
            val baselineScores = baselineRuns.mapNotNull { id ->
                runCatching { regenOpsService.getReproducibilityScore(tenantId, id).score }.getOrNull()
            }
            val baselineAvg = if (baselineScores.isEmpty()) currentScore else baselineScores.average()

            val zMax = computeZScoreMax(dataSource, tenantId, resolvedRunId, metricKey)
            if (zMax >= zThreshold) {
                maybeInsertDriftAlert(
                    regenOpsStore,
                    dataSource,
                    tenantId,
                    resolvedRunId,
                    metricKey,
                    zMax,
                    zThreshold,
                )
            }

            call.respond(
                MetricsScoreResponse(
                    tenantId = tenantId,
                    runId = resolvedRunId,
                    baselineWindow = baselineRuns.size,
                    baselineAvg = baselineAvg,
                    currentScore = currentScore,
                    delta = currentScore - baselineAvg,
                    zScoreMax = zMax,
                ),
            )
        }

        get("/metrics/alerts") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            val runId = call.request.queryParameters["run_id"]?.trim()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
            val alerts = listAlerts(dataSource, tenantId, runId, limit)
            call.respond(
                MetricsAlertsResponse(
                    alerts = alerts,
                ),
            )
        }
    }
}

private fun latestRunId(dataSource: DataSource, tenantId: String): String {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT run_id
            FROM regen_runs
            WHERE tenant_id = ?
            ORDER BY updated_at DESC
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getString("run_id")
                } else {
                    ""
                }
            }
        }
    }
}

private fun listBaselineRuns(
    dataSource: DataSource,
    tenantId: String,
    excludeRunId: String,
    limit: Int,
): List<String> {
    val safeLimit = if (limit <= 0) 5 else limit.coerceAtMost(25)
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT run_id
            FROM regen_runs
            WHERE tenant_id = ? AND run_id <> ?
            ORDER BY updated_at DESC
            LIMIT ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setString(2, excludeRunId)
            statement.setInt(3, safeLimit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(rs.getString("run_id"))
                }
            }
        }
    }
}

private fun computeZScoreMax(
    dataSource: DataSource,
    tenantId: String,
    runId: String,
    metricKey: String,
): Double {
    val values = mutableListOf<Double>()
    dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT metric_value
            FROM regen_run_telemetry
            WHERE tenant_id = ? AND run_id = ? AND metric_key = ?
            ORDER BY recorded_at ASC
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.setString(2, runId)
            statement.setString(3, metricKey)
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    values += rs.getDouble("metric_value")
                }
            }
        }
    }
    if (values.size < 3) return 0.0
    val mean = values.average()
    val variance = values.map { (it - mean) * (it - mean) }.average()
    val stddev = sqrt(variance)
    if (stddev <= 0.0001) return 0.0
    return values.maxOf { abs(it - mean) / stddev }
}

private fun maybeInsertDriftAlert(
    store: RegenOpsStore,
    dataSource: DataSource,
    tenantId: String,
    runId: String,
    metricKey: String,
    zMax: Double,
    threshold: Double,
) {
    val exists =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT COUNT(*) total
                FROM regen_drift_alerts
                WHERE tenant_id = ? AND run_id = ? AND metric_key = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, runId)
                statement.setString(3, metricKey)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt("total") > 0
                }
            }
        }
    if (exists) return
    store.insertDriftAlert(
        RegenDriftAlert(
            tenantId = tenantId,
            runId = runId,
            severity = if (zMax >= threshold * 1.5) "critical" else "warning",
            metricKey = metricKey,
            metricValue = zMax,
            threshold = threshold,
            createdAtMs = Timestamp.from(Instant.now()).time,
        ),
    )
}

private fun listAlerts(
    dataSource: DataSource,
    tenantId: String,
    runId: String?,
    limit: Int,
): List<MetricsAlert> {
    val sql =
        buildString {
            append(
                """
                SELECT tenant_id, run_id, severity, metric_key, metric_value, threshold, created_at
                FROM regen_drift_alerts
                WHERE tenant_id = ?
                """.trimIndent(),
            )
            if (!runId.isNullOrBlank()) append(" AND run_id = ?")
            append(" ORDER BY created_at DESC LIMIT ?")
        }
    return dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            var index = 1
            statement.setString(index++, tenantId)
            if (!runId.isNullOrBlank()) statement.setString(index++, runId)
            statement.setInt(index, max(1, limit.coerceAtMost(500)))
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            MetricsAlert(
                                tenantId = rs.getString("tenant_id"),
                                runId = rs.getString("run_id"),
                                severity = rs.getString("severity"),
                                metricKey = rs.getString("metric_key"),
                                metricValue = rs.getDouble("metric_value"),
                                threshold = rs.getDouble("threshold"),
                                createdAtMs = rs.getTimestamp("created_at").time,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireTenantId(): String {
    val tenantId = request.queryParameters["tenant_id"]
    if (tenantId.isNullOrBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    return tenantId
}

@Serializable
data class MetricsScoreResponse(
    val tenantId: String,
    val runId: String,
    val baselineWindow: Int,
    val baselineAvg: Double,
    val currentScore: Double,
    val delta: Double,
    val zScoreMax: Double,
)

@Serializable
data class MetricsAlert(
    val tenantId: String,
    val runId: String,
    val severity: String,
    val metricKey: String,
    val metricValue: Double,
    val threshold: Double,
    val createdAtMs: Long,
)

@Serializable
data class MetricsAlertsResponse(
    val alerts: List<MetricsAlert>,
)
