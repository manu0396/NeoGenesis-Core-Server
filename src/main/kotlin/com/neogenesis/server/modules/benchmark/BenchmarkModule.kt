package com.neogenesis.server.modules.benchmark

import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

fun Route.benchmarkModule(dataSource: DataSource) {
    authenticate("auth-jwt") {
        get("/benchmark/opt-in") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.respond(
                BenchmarkOptInResponse(
                    tenantId = tenantId,
                    optedIn = readOptIn(dataSource, tenantId),
                ),
            )
        }

        put("/benchmark/opt-in") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            val tenantId = call.requireTenantId()
            val enabled =
                call.request.queryParameters["enabled"]?.toBooleanStrictOrNull()
                    ?: throw ApiException(
                        "invalid_request",
                        "enabled query param is required",
                        HttpStatusCode.BadRequest,
                    )
            upsertOptIn(dataSource, tenantId, enabled)
            call.respond(
                BenchmarkOptInResponse(
                    tenantId = tenantId,
                    optedIn = enabled,
                ),
            )
        }

        get("/benchmark/aggregates") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            if (!readOptIn(dataSource, tenantId)) {
                throw ApiException(
                    "not_opted_in",
                    "tenant has not opted into benchmark aggregation",
                    HttpStatusCode.Forbidden,
                )
            }
            val protocolType = call.request.queryParameters["protocolType"]?.trim()
            val instrumentType = call.request.queryParameters["instrumentType"]?.trim()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
            val aggregates = listAggregates(dataSource, protocolType, instrumentType, limit)
            call.respond(
                BenchmarkAggregateResponse(
                    aggregates = aggregates,
                ),
            )
        }
    }
}

private fun readOptIn(
    dataSource: DataSource,
    tenantId: String,
): Boolean {
    return dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT opted_in
            FROM benchmark_opt_in
            WHERE tenant_id = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, tenantId)
            statement.executeQuery().use { rs ->
                if (rs.next()) rs.getBoolean("opted_in") else false
            }
        }
    }
}

private fun upsertOptIn(
    dataSource: DataSource,
    tenantId: String,
    optedIn: Boolean,
) {
    val now = Timestamp.from(Instant.now())
    val rows =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE benchmark_opt_in
                SET opted_in = ?, updated_at = ?
                WHERE tenant_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setBoolean(1, optedIn)
                statement.setTimestamp(2, now)
                statement.setString(3, tenantId)
                statement.executeUpdate()
            }
        }
    if (rows == 0) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO benchmark_opt_in(tenant_id, opted_in, updated_at)
                VALUES (?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setBoolean(2, optedIn)
                statement.setTimestamp(3, now)
                statement.executeUpdate()
            }
        }
    }
}

private fun listAggregates(
    dataSource: DataSource,
    protocolType: String?,
    instrumentType: String?,
    limit: Int,
): List<BenchmarkAggregate> {
    val sql =
        buildString {
            append(
                """
                SELECT protocol_type, instrument_type, metric_key, sample_count, mean_value, stddev_value, drift_rate, updated_at
                FROM benchmark_aggregates
                """.trimIndent(),
            )
            val filters = mutableListOf<String>()
            if (!protocolType.isNullOrBlank()) filters += "protocol_type = ?"
            if (!instrumentType.isNullOrBlank()) filters += "instrument_type = ?"
            if (filters.isNotEmpty()) {
                append(" WHERE ")
                append(filters.joinToString(" AND "))
            }
            append(" ORDER BY updated_at DESC LIMIT ?")
        }
    return dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            var index = 1
            if (!protocolType.isNullOrBlank()) statement.setString(index++, protocolType)
            if (!instrumentType.isNullOrBlank()) statement.setString(index++, instrumentType)
            statement.setInt(index, limit)
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            BenchmarkAggregate(
                                protocolType = rs.getString("protocol_type"),
                                instrumentType = rs.getString("instrument_type"),
                                metricKey = rs.getString("metric_key"),
                                sampleCount = rs.getInt("sample_count"),
                                meanValue = rs.getDouble("mean_value"),
                                stddevValue = rs.getDouble("stddev_value"),
                                driftRate = rs.getDouble("drift_rate"),
                                updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
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
data class BenchmarkOptInResponse(
    val tenantId: String,
    val optedIn: Boolean,
)

@Serializable
data class BenchmarkAggregate(
    val protocolType: String,
    val instrumentType: String,
    val metricKey: String,
    val sampleCount: Int,
    val meanValue: Double,
    val stddevValue: Double,
    val driftRate: Double,
    val updatedAt: String,
)

@Serializable
data class BenchmarkAggregateResponse(
    val aggregates: List<BenchmarkAggregate>,
)
