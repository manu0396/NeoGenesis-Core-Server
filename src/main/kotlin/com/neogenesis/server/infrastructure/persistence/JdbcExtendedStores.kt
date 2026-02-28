package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.port.LatencyBreachStore
import com.neogenesis.server.application.port.OutboxEventStore
import com.neogenesis.server.application.port.PrintSessionStore
import com.neogenesis.server.application.port.RetinalPlanStore
import com.neogenesis.server.domain.model.DeadLetterOutboxEvent
import com.neogenesis.server.domain.model.LatencyBreachEvent
import com.neogenesis.server.domain.model.OutboxEventStatus
import com.neogenesis.server.domain.model.PrintSession
import com.neogenesis.server.domain.model.PrintSessionStatus
import com.neogenesis.server.domain.model.RetinalControlConstraints
import com.neogenesis.server.domain.model.RetinalLayerSpec
import com.neogenesis.server.domain.model.RetinalPrintPlan
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

private val extendedJson = Json { ignoreUnknownKeys = true }

class JdbcRetinalPlanStore(private val dataSource: DataSource) : RetinalPlanStore {
    override fun save(plan: RetinalPrintPlan) {
        val tenantId = plan.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            val updated =
                connection.prepareStatement(
                    """
                    UPDATE retinal_print_plans
                    SET
                        patient_id = ?,
                        source_document_id = ?,
                        blueprint_version = ?,
                        layers_json = ?,
                        constraints_json = ?,
                        created_at = ?
                    WHERE tenant_id = ? AND plan_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, plan.patientId)
                    statement.setString(2, plan.sourceDocumentId)
                    statement.setString(3, plan.blueprintVersion)
                    statement.setString(4, encodeLayers(plan.layers))
                    statement.setString(5, encodeConstraints(plan.constraints))
                    statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(plan.createdAtMs)))
                    statement.setString(7, tenantId)
                    statement.setString(8, plan.planId)
                    statement.executeUpdate()
                }

            if (updated == 0) {
                connection.prepareStatement(
                    """
                    INSERT INTO retinal_print_plans(
                        tenant_id,
                        plan_id,
                        patient_id,
                        source_document_id,
                        blueprint_version,
                        layers_json,
                        constraints_json,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, tenantId)
                    statement.setString(2, plan.planId)
                    statement.setString(3, plan.patientId)
                    statement.setString(4, plan.sourceDocumentId)
                    statement.setString(5, plan.blueprintVersion)
                    statement.setString(6, encodeLayers(plan.layers))
                    statement.setString(7, encodeConstraints(plan.constraints))
                    statement.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(plan.createdAtMs)))
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun findByPlanId(
        tenantId: String,
        planId: String,
    ): RetinalPrintPlan? {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    plan_id,
                    patient_id,
                    source_document_id,
                    blueprint_version,
                    layers_json,
                    constraints_json,
                    created_at
                FROM retinal_print_plans
                WHERE tenant_id = ? AND plan_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, planId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toRetinalPlan() else null
                }
            }
        }
    }

    override fun findLatestByPatientId(
        tenantId: String,
        patientId: String,
    ): RetinalPrintPlan? {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    plan_id,
                    patient_id,
                    source_document_id,
                    blueprint_version,
                    layers_json,
                    constraints_json,
                    created_at
                FROM retinal_print_plans
                WHERE tenant_id = ? AND patient_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, patientId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toRetinalPlan() else null
                }
            }
        }
    }

    override fun findRecent(
        tenantId: String,
        limit: Int,
    ): List<RetinalPrintPlan> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    plan_id,
                    patient_id,
                    source_document_id,
                    blueprint_version,
                    layers_json,
                    constraints_json,
                    created_at
                FROM retinal_print_plans
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toRetinalPlan())
                        }
                    }
                }
            }
        }
    }
}

class JdbcPrintSessionStore(private val dataSource: DataSource) : PrintSessionStore {
    override fun create(session: PrintSession) {
        val tenantId = session.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO print_sessions(
                    tenant_id,
                    session_id,
                    printer_id,
                    plan_id,
                    patient_id,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, session.sessionId)
                statement.setString(3, session.printerId)
                statement.setString(4, session.planId)
                statement.setString(5, session.patientId)
                statement.setString(6, session.status.name)
                statement.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(session.createdAtMs)))
                statement.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(session.updatedAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun updateStatus(
        tenantId: String,
        sessionId: String,
        status: PrintSessionStatus,
        updatedAtMs: Long,
    ) {
        dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                UPDATE print_sessions
                SET
                    status = ?,
                    updated_at = ?
                WHERE tenant_id = ? AND session_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, status.name)
                statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(updatedAtMs)))
                statement.setString(3, tenantId)
                statement.setString(4, sessionId)
                statement.executeUpdate()
            }
        }
    }

    override fun findBySessionId(
        tenantId: String,
        sessionId: String,
    ): PrintSession? {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    session_id,
                    printer_id,
                    plan_id,
                    patient_id,
                    status,
                    created_at,
                    updated_at
                FROM print_sessions
                WHERE tenant_id = ? AND session_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, sessionId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toPrintSession() else null
                }
            }
        }
    }

    override fun findActiveByPrinterId(
        tenantId: String,
        printerId: String,
    ): PrintSession? {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    session_id,
                    printer_id,
                    plan_id,
                    patient_id,
                    status,
                    created_at,
                    updated_at
                FROM print_sessions
                WHERE tenant_id = ? AND printer_id = ?
                  AND status = 'ACTIVE'
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, printerId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toPrintSession() else null
                }
            }
        }
    }

    override fun findActive(
        tenantId: String,
        limit: Int,
    ): List<PrintSession> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    session_id,
                    printer_id,
                    plan_id,
                    patient_id,
                    status,
                    created_at,
                    updated_at
                FROM print_sessions
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY updated_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toPrintSession())
                        }
                    }
                }
            }
        }
    }
}

class JdbcLatencyBreachStore(private val dataSource: DataSource) : LatencyBreachStore {
    override fun append(event: LatencyBreachEvent) {
        val tenantId = event.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO latency_budget_breaches(
                    tenant_id,
                    printer_id,
                    source,
                    duration_ms,
                    threshold_ms,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, event.printerId)
                statement.setString(3, event.source)
                statement.setDouble(4, event.durationMs)
                statement.setLong(5, event.thresholdMs)
                statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(event.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recent(
        tenantId: String,
        limit: Int,
    ): List<LatencyBreachEvent> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    printer_id,
                    source,
                    duration_ms,
                    threshold_ms,
                    created_at
                FROM latency_budget_breaches
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                LatencyBreachEvent(
                                    tenantId = rs.getString("tenant_id"),
                                    printerId = rs.getString("printer_id"),
                                    source = rs.getString("source"),
                                    durationMs = rs.getDouble("duration_ms"),
                                    thresholdMs = rs.getLong("threshold_ms"),
                                    createdAtMs = rs.getTimestamp("created_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class JdbcOutboxEventStore(val dataSource: DataSource) : OutboxEventStore {
    override fun enqueue(
        eventType: String,
        partitionKey: String,
        payloadJson: String,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO integration_outbox(
                    event_type,
                    partition_key,
                    payload_json,
                    status,
                    attempts,
                    next_attempt_at,
                    processing_started_at,
                    last_error,
                    created_at,
                    processed_at
                ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                val now = Timestamp.from(Instant.now())
                statement.setString(1, eventType)
                statement.setString(2, partitionKey)
                statement.setString(3, payloadJson)
                statement.setTimestamp(4, now)
                statement.setTimestamp(5, null)
                statement.setString(6, null)
                statement.setTimestamp(7, now)
                statement.setTimestamp(8, null)
                statement.executeUpdate()
            }
        }
    }

    override fun pending(limit: Int): List<ServerlessOutboxEvent> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    event_type,
                    partition_key,
                    payload_json,
                    status,
                    attempts,
                    next_attempt_at,
                    processing_started_at,
                    last_error,
                    created_at,
                    processed_at
                FROM integration_outbox
                WHERE status = 'PENDING'
                  AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toOutboxEvent())
                        }
                    }
                }
            }
        }
    }

    override fun claimPending(
        limit: Int,
        processingTtlMs: Long,
    ): List<ServerlessOutboxEvent> {
        if (limit <= 0) {
            return emptyList()
        }

        return try {
            claimPendingWithSkipLocked(limit = limit, processingTtlMs = processingTtlMs)
        } catch (_: SQLException) {
            claimPendingWithOptimisticClaim(limit = limit, processingTtlMs = processingTtlMs)
        }
    }

    override fun markProcessed(eventId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE integration_outbox
                SET
                    status = 'PROCESSED',
                    attempts = attempts + 1,
                    processed_at = ?,
                    processing_started_at = NULL,
                    next_attempt_at = NULL,
                    last_error = NULL
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(Instant.now()))
                statement.setLong(2, eventId)
                statement.executeUpdate()
            }
        }
    }

    override fun scheduleRetry(
        eventId: Long,
        nextAttemptAtMs: Long,
        failureReason: String,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE integration_outbox
                SET
                    status = 'PENDING',
                    attempts = attempts + 1,
                    next_attempt_at = ?,
                    processing_started_at = NULL,
                    last_error = ?,
                    processed_at = NULL
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, Timestamp.from(Instant.ofEpochMilli(nextAttemptAtMs)))
                statement.setString(2, failureReason.take(2048))
                statement.setLong(3, eventId)
                statement.executeUpdate()
            }
        }
    }

    override fun moveToDeadLetter(
        eventId: Long,
        failureReason: String,
    ) {
        dataSource.connection.use { connection ->
            val now = Timestamp.from(Instant.now())

            connection.prepareStatement(
                """
                INSERT INTO integration_outbox_dead_letter(
                    source_outbox_id,
                    event_type,
                    partition_key,
                    payload_json,
                    attempts,
                    failure_reason,
                    created_at,
                    failed_at
                )
                SELECT
                    id,
                    event_type,
                    partition_key,
                    payload_json,
                    attempts + 1,
                    ?,
                    created_at,
                    ?
                FROM integration_outbox
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, failureReason.take(2048))
                statement.setTimestamp(2, now)
                statement.setLong(3, eventId)
                statement.executeUpdate()
            }

            connection.prepareStatement(
                """
                UPDATE integration_outbox
                SET
                    status = 'FAILED',
                    attempts = attempts + 1,
                    processed_at = ?,
                    processing_started_at = NULL,
                    next_attempt_at = NULL,
                    last_error = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setTimestamp(1, now)
                statement.setString(2, failureReason.take(2048))
                statement.setLong(3, eventId)
                statement.executeUpdate()
            }
        }
    }

    override fun deadLetter(limit: Int): List<DeadLetterOutboxEvent> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    source_outbox_id,
                    event_type,
                    partition_key,
                    payload_json,
                    attempts,
                    failure_reason,
                    created_at,
                    failed_at
                FROM integration_outbox_dead_letter
                ORDER BY failed_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toDeadLetterEvent())
                        }
                    }
                }
            }
        }
    }

    override fun replayDeadLetter(deadLetterId: Long): Boolean {
        return dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val moved =
                    connection.prepareStatement(
                        """
                        INSERT INTO integration_outbox(
                            event_type,
                            partition_key,
                            payload_json,
                            status,
                            attempts,
                            next_attempt_at,
                            processing_started_at,
                            last_error,
                            created_at,
                            processed_at
                        )
                        SELECT
                            event_type,
                            partition_key,
                            payload_json,
                            'PENDING',
                            0,
                            CURRENT_TIMESTAMP,
                            NULL,
                            NULL,
                            CURRENT_TIMESTAMP,
                            NULL
                        FROM integration_outbox_dead_letter
                        WHERE id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, deadLetterId)
                        statement.executeUpdate()
                    }
                if (moved == 0) {
                    connection.rollback()
                    return false
                }

                connection.prepareStatement(
                    """
                    DELETE FROM integration_outbox_dead_letter
                    WHERE id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, deadLetterId)
                    statement.executeUpdate()
                }
                connection.commit()
                true
            } catch (_: Exception) {
                connection.rollback()
                false
            } finally {
                connection.autoCommit = true
            }
        }
    }
}

private fun JdbcOutboxEventStore.claimPendingWithSkipLocked(
    limit: Int,
    processingTtlMs: Long,
): List<ServerlessOutboxEvent> {
    return dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            releaseExpiredClaims(connection, processingTtlMs)
            val ids =
                connection.prepareStatement(
                    """
                    SELECT id
                    FROM integration_outbox
                    WHERE status = 'PENDING'
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    ORDER BY id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.getLong("id"))
                            }
                        }
                    }
                }

            if (ids.isEmpty()) {
                connection.commit()
                return emptyList()
            }

            connection.prepareStatement(
                """
                UPDATE integration_outbox
                SET
                    status = 'PROCESSING',
                    processing_started_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                """.trimIndent(),
            ).use { statement ->
                ids.forEach { id ->
                    statement.setLong(1, id)
                    statement.addBatch()
                }
                statement.executeBatch()
            }

            val events = selectOutboxEventsByIds(connection, ids)
            connection.commit()
            events
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }
}

private fun JdbcOutboxEventStore.claimPendingWithOptimisticClaim(
    limit: Int,
    processingTtlMs: Long,
): List<ServerlessOutboxEvent> {
    return dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            releaseExpiredClaims(connection, processingTtlMs)
            val candidates =
                connection.prepareStatement(
                    """
                    SELECT id
                    FROM integration_outbox
                    WHERE status = 'PENDING'
                      AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
                    ORDER BY id ASC
                    LIMIT ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) {
                                add(rs.getLong("id"))
                            }
                        }
                    }
                }

            val claimed = mutableListOf<Long>()
            connection.prepareStatement(
                """
                UPDATE integration_outbox
                SET
                    status = 'PROCESSING',
                    processing_started_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND status = 'PENDING'
                """.trimIndent(),
            ).use { statement ->
                candidates.forEach { id ->
                    statement.setLong(1, id)
                    val updated = statement.executeUpdate()
                    if (updated > 0) {
                        claimed += id
                    }
                }
            }

            val events = selectOutboxEventsByIds(connection, claimed)
            connection.commit()
            events
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }
}

private fun JdbcOutboxEventStore.releaseExpiredClaims(
    connection: java.sql.Connection,
    processingTtlMs: Long,
) {
    val ttlMs = processingTtlMs.coerceAtLeast(60_000L)
    val cutoff = Timestamp.from(Instant.ofEpochMilli(System.currentTimeMillis() - ttlMs))
    connection.prepareStatement(
        """
        UPDATE integration_outbox
        SET
            status = 'PENDING',
            processing_started_at = NULL
        WHERE status = 'PROCESSING'
          AND processing_started_at IS NOT NULL
          AND processing_started_at < ?
        """.trimIndent(),
    ).use { statement ->
        statement.setTimestamp(1, cutoff)
        statement.executeUpdate()
    }
}

private fun JdbcOutboxEventStore.selectOutboxEventsByIds(
    connection: java.sql.Connection,
    ids: List<Long>,
): List<ServerlessOutboxEvent> {
    if (ids.isEmpty()) {
        return emptyList()
    }
    val placeholders = ids.joinToString(",") { "?" }
    return connection.prepareStatement(
        """
        SELECT
            id,
            event_type,
            partition_key,
            payload_json,
            status,
            attempts,
            next_attempt_at,
            processing_started_at,
            last_error,
            created_at,
            processed_at
        FROM integration_outbox
        WHERE id IN ($placeholders)
          AND status = 'PROCESSING'
        ORDER BY id ASC
        """.trimIndent(),
    ).use { statement ->
        ids.forEachIndexed { index, id ->
            statement.setLong(index + 1, id)
        }
        statement.executeQuery().use { rs ->
            buildList {
                while (rs.next()) {
                    add(rs.toOutboxEvent())
                }
            }
        }
    }
}

private fun ResultSet.toRetinalPlan(): RetinalPrintPlan {
    return RetinalPrintPlan(
        tenantId = getString("tenant_id"),
        planId = getString("plan_id"),
        patientId = getString("patient_id"),
        sourceDocumentId = getString("source_document_id"),
        blueprintVersion = getString("blueprint_version"),
        layers = decodeLayers(getString("layers_json")),
        constraints = decodeConstraints(getString("constraints_json")),
        createdAtMs = getTimestamp("created_at").time,
    )
}

private fun ResultSet.toPrintSession(): PrintSession {
    return PrintSession(
        tenantId = getString("tenant_id"),
        sessionId = getString("session_id"),
        printerId = getString("printer_id"),
        planId = getString("plan_id"),
        patientId = getString("patient_id"),
        status = PrintSessionStatus.valueOf(getString("status")),
        createdAtMs = getTimestamp("created_at").time,
        updatedAtMs = getTimestamp("updated_at").time,
    )
}

private fun ResultSet.toOutboxEvent(): ServerlessOutboxEvent {
    val processedAt = getTimestamp("processed_at")
    val nextAttemptAt = getTimestamp("next_attempt_at")
    val processingStartedAt = runCatching { getTimestamp("processing_started_at") }.getOrNull()
    return ServerlessOutboxEvent(
        id = getLong("id"),
        eventType = getString("event_type"),
        partitionKey = getString("partition_key"),
        payloadJson = getString("payload_json"),
        status = OutboxEventStatus.valueOf(getString("status")),
        attempts = getInt("attempts"),
        createdAtMs = getTimestamp("created_at").time,
        processingStartedAtMs = processingStartedAt?.time,
        processedAtMs = processedAt?.time,
        nextAttemptAtMs = nextAttemptAt?.time,
        lastError = getString("last_error"),
    )
}

private fun ResultSet.toDeadLetterEvent(): DeadLetterOutboxEvent {
    return DeadLetterOutboxEvent(
        id = getLong("id"),
        sourceOutboxId = getLong("source_outbox_id"),
        eventType = getString("event_type"),
        partitionKey = getString("partition_key"),
        payloadJson = getString("payload_json"),
        attempts = getInt("attempts"),
        failureReason = getString("failure_reason"),
        createdAtMs = getTimestamp("created_at").time,
        failedAtMs = getTimestamp("failed_at").time,
    )
}

private fun encodeLayers(value: List<RetinalLayerSpec>): String {
    return extendedJson.encodeToString(ListSerializer(RetinalLayerSpec.serializer()), value)
}

private fun decodeLayers(value: String?): List<RetinalLayerSpec> {
    if (value.isNullOrBlank()) {
        return emptyList()
    }
    return runCatching {
        extendedJson.decodeFromString(ListSerializer(RetinalLayerSpec.serializer()), value)
    }.getOrElse {
        emptyList()
    }
}

private fun encodeConstraints(value: RetinalControlConstraints): String {
    return extendedJson.encodeToString(RetinalControlConstraints.serializer(), value)
}

private fun decodeConstraints(value: String?): RetinalControlConstraints {
    if (value.isNullOrBlank()) {
        return defaultConstraints()
    }
    return runCatching {
        extendedJson.decodeFromString(RetinalControlConstraints.serializer(), value)
    }.getOrElse {
        defaultConstraints()
    }
}

private fun defaultConstraints(): RetinalControlConstraints {
    return RetinalControlConstraints(
        targetNozzleTempCelsius = 36.8f,
        tempToleranceCelsius = 0.8f,
        targetPressureKPa = 108.0f,
        pressureToleranceKPa = 8.0f,
        minCellViability = 0.9f,
        maxMorphologicalDefectProbability = 0.12f,
        maxNirIiTempCelsius = 38.5f,
        targetBioInkPh = 7.35f,
        phTolerance = 0.12f,
    )
}
