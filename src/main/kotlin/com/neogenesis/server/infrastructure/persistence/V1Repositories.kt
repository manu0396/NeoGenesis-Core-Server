package com.neogenesis.server.infrastructure.persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

enum class CanonicalRole {
    ADMIN,
    OPERATOR,
    AUDITOR,
    INTEGRATION,
}

data class UserRecord(
    val id: String,
    val username: String,
    val passwordHash: String,
    val role: CanonicalRole,
    val tenantId: String?,
    val isActive: Boolean,
)

data class DeviceRecord(
    val id: String,
    val tenantId: String?,
    val name: String,
    val createdAt: Instant,
)

data class JobRecord(
    val id: String,
    val deviceId: String,
    val tenantId: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class TelemetryRecord(
    val id: Long,
    val jobId: String,
    val deviceId: String,
    val payloadJson: String,
    val recordedAt: Instant,
)

data class TwinMetricRecord(
    val id: Long,
    val jobId: String,
    val deviceId: String,
    val payloadJson: String,
    val recordedAt: Instant,
)

data class AuditLogRecord(
    val id: Long,
    val jobId: String?,
    val actorId: String,
    val eventType: String,
    val deviceId: String?,
    val payloadJson: String,
    val payloadHash: String,
    val prevHash: String?,
    val eventHash: String,
    val createdAt: Instant,
)

data class AuditChainStatus(
    val valid: Boolean,
    val checked: Int,
    val failureIndex: Int?,
    val reason: String?,
)

class UserRepository(private val dataSource: DataSource) {
    fun findByUsername(username: String): UserRecord? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, username, password_hash, role, tenant_id, is_active
                FROM users
                WHERE username = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        UserRecord(
                            id = rs.getString("id"),
                            username = rs.getString("username"),
                            passwordHash = rs.getString("password_hash"),
                            role = CanonicalRole.valueOf(rs.getString("role")),
                            tenantId = rs.getString("tenant_id"),
                            isActive = rs.getBoolean("is_active"),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun upsertBootstrapAdmin(
        username: String,
        passwordHash: String,
        role: CanonicalRole = CanonicalRole.ADMIN,
        tenantId: String? = null,
    ): UserRecord {
        return dataSource.inTransaction { connection ->
            val existingId =
                connection.prepareStatement(
                    """
                    SELECT id
                    FROM users
                    WHERE username = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, username)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("id") else null
                    }
                }

            val userId = existingId ?: UUID.randomUUID().toString()

            if (existingId == null) {
                connection.prepareStatement(
                    """
                    INSERT INTO users(id, username, password_hash, role, tenant_id, is_active, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    val now = Timestamp.from(Instant.now())
                    statement.setString(1, userId)
                    statement.setString(2, username)
                    statement.setString(3, passwordHash)
                    statement.setString(4, role.name)
                    statement.setString(5, tenantId)
                    statement.setBoolean(6, true)
                    statement.setTimestamp(7, now)
                    statement.setTimestamp(8, now)
                    statement.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    """
                    UPDATE users
                    SET password_hash = ?, role = ?, tenant_id = COALESCE(?, tenant_id), is_active = TRUE, updated_at = ?
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, passwordHash)
                    statement.setString(2, role.name)
                    statement.setString(3, tenantId)
                    statement.setTimestamp(4, Timestamp.from(Instant.now()))
                    statement.setString(5, userId)
                    statement.executeUpdate()
                }
            }

            connection.prepareStatement(
                """
                SELECT id, username, password_hash, role, tenant_id, is_active
                FROM users
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, userId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        UserRecord(
                            id = rs.getString("id"),
                            username = rs.getString("username"),
                            passwordHash = rs.getString("password_hash"),
                            role = CanonicalRole.valueOf(rs.getString("role")),
                            tenantId = rs.getString("tenant_id"),
                            isActive = rs.getBoolean("is_active"),
                        )
                    } else {
                        error("Failed to upsert bootstrap user")
                    }
                }
            }
        }
    }
}

class DeviceRepository(private val dataSource: DataSource) {
    private fun Connection.setTenant(tenantId: String?) {
        if (tenantId != null) {
            val isPostgres = metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
            if (isPostgres) {
                prepareStatement("SET LOCAL app.current_tenant = ?").use { s ->
                    s.setString(1, tenantId)
                    s.execute()
                }
            }
        }
    }

    fun create(
        id: String,
        tenantId: String?,
        name: String,
    ): DeviceRecord {
        val now = Instant.now()
        dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                INSERT INTO devices(id, tenant_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val nowTs = Timestamp.from(now)
                statement.setString(1, id)
                statement.setString(2, tenantId)
                statement.setString(3, name)
                statement.setTimestamp(4, nowTs)
                statement.setTimestamp(5, nowTs)
                statement.executeUpdate()
            }
        }
        return DeviceRecord(id = id, tenantId = tenantId, name = name, createdAt = now)
    }

    fun find(
        tenantId: String?,
        id: String,
    ): DeviceRecord? {
        return dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                SELECT id, tenant_id, name, created_at
                FROM devices
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        DeviceRecord(
                            id = rs.getString("id"),
                            tenantId = rs.getString("tenant_id"),
                            name = rs.getString("name"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun list(
        tenantId: String?,
        limit: Int,
    ): List<DeviceRecord> {
        return dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                SELECT id, tenant_id, name, created_at
                FROM devices
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                DeviceRecord(
                                    id = rs.getString("id"),
                                    tenantId = rs.getString("tenant_id"),
                                    name = rs.getString("name"),
                                    createdAt = rs.getTimestamp("created_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class JobRepository(private val dataSource: DataSource) {
    private fun Connection.setTenant(tenantId: String?) {
        if (tenantId != null) {
            val isPostgres = metaData.databaseProductName.contains("PostgreSQL", ignoreCase = true)
            if (isPostgres) {
                prepareStatement("SET LOCAL app.current_tenant = ?").use { s ->
                    s.setString(1, tenantId)
                    s.execute()
                }
            }
        }
    }

    fun create(
        id: String,
        deviceId: String,
        tenantId: String?,
        status: String = "CREATED",
    ): JobRecord {
        val now = Instant.now()
        dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                INSERT INTO print_jobs(id, device_id, tenant_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val nowTs = Timestamp.from(now)
                statement.setString(1, id)
                statement.setString(2, deviceId)
                statement.setString(3, tenantId)
                statement.setString(4, status)
                statement.setTimestamp(5, nowTs)
                statement.setTimestamp(6, nowTs)
                statement.executeUpdate()
            }
        }
        return get(tenantId, id) ?: error("Failed to create print job")
    }

    fun get(
        tenantId: String?,
        id: String,
    ): JobRecord? {
        return dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                SELECT id, device_id, tenant_id, status, created_at, updated_at
                FROM print_jobs
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        JobRecord(
                            id = rs.getString("id"),
                            deviceId = rs.getString("device_id"),
                            tenantId = rs.getString("tenant_id"),
                            status = rs.getString("status"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                            updatedAt = rs.getTimestamp("updated_at").toInstant(),
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    fun list(
        tenantId: String?,
        limit: Int,
    ): List<JobRecord> {
        return dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                SELECT id, device_id, tenant_id, status, created_at, updated_at
                FROM print_jobs
                ORDER BY updated_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                JobRecord(
                                    id = rs.getString("id"),
                                    deviceId = rs.getString("device_id"),
                                    tenantId = rs.getString("tenant_id"),
                                    status = rs.getString("status"),
                                    createdAt = rs.getTimestamp("created_at").toInstant(),
                                    updatedAt = rs.getTimestamp("updated_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateStatus(
        tenantId: String?,
        id: String,
        status: String,
    ): JobRecord? {
        dataSource.inTransaction { connection ->
            connection.setTenant(tenantId)
            connection.prepareStatement(
                """
                UPDATE print_jobs
                SET status = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, status)
                statement.setTimestamp(2, Timestamp.from(Instant.now()))
                statement.setString(3, id)
                statement.executeUpdate()
            }
        }
        return get(tenantId, id)
    }
}

class TelemetryRepository(private val dataSource: DataSource) {
    fun insert(
        jobId: String,
        deviceId: String,
        payload: JsonElement,
        recordedAt: Instant,
    ): TelemetryRecord {
        return dataSource.inTransaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO telemetry_records(job_id, device_id, payload_json, recorded_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setString(2, deviceId)
                statement.setString(3, canonicalJson(payload))
                statement.setTimestamp(4, Timestamp.from(recordedAt))
                statement.executeUpdate()
                statement.generatedKeys.use { generated ->
                    generated.next()
                    TelemetryRecord(
                        id = generated.getLong(1),
                        jobId = jobId,
                        deviceId = deviceId,
                        payloadJson = canonicalJson(payload),
                        recordedAt = recordedAt,
                    )
                }
            }
        }
    }

    fun listByJob(
        jobId: String,
        from: Instant?,
        to: Instant?,
        limit: Int,
    ): List<TelemetryRecord> {
        val sql =
            buildString {
                append(
                    """
                    SELECT id, job_id, device_id, payload_json, recorded_at
                    FROM telemetry_records
                    WHERE job_id = ?
                    """.trimIndent(),
                )
                if (from != null) append(" AND recorded_at >= ?")
                if (to != null) append(" AND recorded_at <= ?")
                append(" ORDER BY recorded_at DESC LIMIT ?")
            }

        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, jobId)
                if (from != null) {
                    statement.setTimestamp(index++, Timestamp.from(from))
                }
                if (to != null) {
                    statement.setTimestamp(index++, Timestamp.from(to))
                }
                statement.setInt(index, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                TelemetryRecord(
                                    id = rs.getLong("id"),
                                    jobId = rs.getString("job_id"),
                                    deviceId = rs.getString("device_id"),
                                    payloadJson = rs.getString("payload_json"),
                                    recordedAt = rs.getTimestamp("recorded_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class TwinMetricsRepository(private val dataSource: DataSource) {
    fun insert(
        jobId: String,
        deviceId: String,
        payload: JsonElement,
        recordedAt: Instant,
    ): TwinMetricRecord {
        return dataSource.inTransaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO digital_twin_metrics(job_id, device_id, payload_json, recorded_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setString(2, deviceId)
                statement.setString(3, canonicalJson(payload))
                statement.setTimestamp(4, Timestamp.from(recordedAt))
                statement.executeUpdate()
                statement.generatedKeys.use { generated ->
                    generated.next()
                    TwinMetricRecord(
                        id = generated.getLong(1),
                        jobId = jobId,
                        deviceId = deviceId,
                        payloadJson = canonicalJson(payload),
                        recordedAt = recordedAt,
                    )
                }
            }
        }
    }

    fun listByJob(
        jobId: String,
        from: Instant?,
        to: Instant?,
        limit: Int,
    ): List<TwinMetricRecord> {
        val sql =
            buildString {
                append(
                    """
                    SELECT id, job_id, device_id, payload_json, recorded_at
                    FROM digital_twin_metrics
                    WHERE job_id = ?
                    """.trimIndent(),
                )
                if (from != null) append(" AND recorded_at >= ?")
                if (to != null) append(" AND recorded_at <= ?")
                append(" ORDER BY recorded_at DESC LIMIT ?")
            }

        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, jobId)
                if (from != null) {
                    statement.setTimestamp(index++, Timestamp.from(from))
                }
                if (to != null) {
                    statement.setTimestamp(index++, Timestamp.from(to))
                }
                statement.setInt(index, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                TwinMetricRecord(
                                    id = rs.getLong("id"),
                                    jobId = rs.getString("job_id"),
                                    deviceId = rs.getString("device_id"),
                                    payloadJson = rs.getString("payload_json"),
                                    recordedAt = rs.getTimestamp("recorded_at").toInstant(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class AuditLogRepository(private val dataSource: DataSource) {
    fun lastHash(jobId: String?): String? {
        return dataSource.connection.use { connection ->
            val sql =
                if (jobId == null) {
                    """
                    SELECT event_hash
                    FROM audit_logs
                    WHERE job_id IS NULL
                    ORDER BY id DESC
                    LIMIT 1
                    """.trimIndent()
                } else {
                    """
                    SELECT event_hash
                    FROM audit_logs
                    WHERE job_id = ?
                    ORDER BY id DESC
                    LIMIT 1
                    """.trimIndent()
                }
            connection.prepareStatement(sql).use { statement ->
                if (jobId != null) {
                    statement.setString(1, jobId)
                }
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("event_hash") else null
                }
            }
        }
    }

    fun append(
        jobId: String?,
        actorId: String,
        eventType: String,
        deviceId: String?,
        payload: JsonElement,
        createdAt: Instant = Instant.now(),
    ): AuditLogRecord {
        val payloadCanonical = canonicalJson(payload)
        val payloadHash = sha256(payloadCanonical)
        val prevHash = lastHash(jobId)
        val eventHash =
            sha256(
                "${prevHash.orEmpty()}$payloadHash${createdAt.toEpochMilli()}$eventType$actorId${deviceId.orEmpty()}${jobId.orEmpty()}",
            )

        return dataSource.inTransaction { connection ->
            connection.prepareStatement(
                """
                INSERT INTO audit_logs(
                    job_id,
                    actor_id,
                    event_type,
                    device_id,
                    payload_json,
                    payload_hash,
                    prev_hash,
                    event_hash,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setString(2, actorId)
                statement.setString(3, eventType)
                statement.setString(4, deviceId)
                statement.setString(5, payloadCanonical)
                statement.setString(6, payloadHash)
                statement.setString(7, prevHash)
                statement.setString(8, eventHash)
                statement.setTimestamp(9, Timestamp.from(createdAt))
                statement.executeUpdate()
                statement.generatedKeys.use { generated ->
                    generated.next()
                    AuditLogRecord(
                        id = generated.getLong(1),
                        jobId = jobId,
                        actorId = actorId,
                        eventType = eventType,
                        deviceId = deviceId,
                        payloadJson = payloadCanonical,
                        payloadHash = payloadHash,
                        prevHash = prevHash,
                        eventHash = eventHash,
                        createdAt = createdAt,
                    )
                }
            }
        }
    }

    fun listByJob(
        jobId: String,
        limit: Int,
    ): List<AuditLogRecord> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, job_id, actor_id, event_type, device_id, payload_json, payload_hash, prev_hash, event_hash, created_at
                FROM audit_logs
                WHERE job_id = ?
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, jobId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toAuditLogRecord())
                        }
                    }
                }
            }
        }
    }

    fun verifyJobChain(
        jobId: String,
        limit: Int = 10_000,
    ): AuditChainStatus {
        val records = listByJob(jobId, limit)
        var expectedPrev: String? = null
        records.forEachIndexed { index, record ->
            if (record.prevHash != expectedPrev) {
                return AuditChainStatus(
                    valid = false,
                    checked = index + 1,
                    failureIndex = index + 1,
                    reason = "previous hash mismatch",
                )
            }
            val recomputedPayloadHash = sha256(record.payloadJson)
            if (recomputedPayloadHash != record.payloadHash) {
                return AuditChainStatus(
                    valid = false,
                    checked = index + 1,
                    failureIndex = index + 1,
                    reason = "payload hash mismatch",
                )
            }
            val recomputedEventHash =
                sha256(
                    record.prevHash.orEmpty() +
                        record.payloadHash +
                        record.createdAt.toEpochMilli() +
                        record.eventType +
                        record.actorId +
                        record.deviceId.orEmpty() +
                        record.jobId.orEmpty(),
                )
            if (recomputedEventHash != record.eventHash) {
                return AuditChainStatus(
                    valid = false,
                    checked = index + 1,
                    failureIndex = index + 1,
                    reason = "event hash mismatch",
                )
            }
            expectedPrev = record.eventHash
        }
        return AuditChainStatus(
            valid = true,
            checked = records.size,
            failureIndex = null,
            reason = null,
        )
    }
}

private fun <T> DataSource.inTransaction(block: (Connection) -> T): T {
    connection.use { connection ->
        val initialAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val value = block(connection)
            connection.commit()
            return value
        } catch (ex: Throwable) {
            connection.rollback()
            throw ex
        } finally {
            connection.autoCommit = initialAutoCommit
        }
    }
}

private fun java.sql.ResultSet.toAuditLogRecord(): AuditLogRecord {
    return AuditLogRecord(
        id = getLong("id"),
        jobId = getString("job_id"),
        actorId = getString("actor_id"),
        eventType = getString("event_type"),
        deviceId = getString("device_id"),
        payloadJson = getString("payload_json"),
        payloadHash = getString("payload_hash"),
        prevHash = getString("prev_hash"),
        eventHash = getString("event_hash"),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}

private fun canonicalJson(element: JsonElement): String {
    return when (element) {
        is JsonObject -> {
            element.entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { entry ->
                "${Json.encodeToString(entry.key)}:${canonicalJson(entry.value)}"
            }
        }

        is JsonArray -> {
            element.joinToString(prefix = "[", postfix = "]") { canonicalJson(it) }
        }

        is JsonPrimitive -> {
            when {
                element.isString -> Json.encodeToString(element.content)
                else -> element.content
            }
        }
    }
}

private fun sha256(value: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

fun parsePayload(payloadJson: String): JsonObject {
    return runCatching { Json.parseToJsonElement(payloadJson).jsonObject }
        .getOrDefault(buildJsonObject { })
}
