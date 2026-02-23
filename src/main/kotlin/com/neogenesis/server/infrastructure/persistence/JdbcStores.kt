package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.port.AuditEventStore
import com.neogenesis.server.application.port.ClinicalDocumentStore
import com.neogenesis.server.application.port.ControlCommandStore
import com.neogenesis.server.application.port.DigitalTwinStore
import com.neogenesis.server.application.port.TelemetryEventStore
import com.neogenesis.server.domain.model.AuditChainVerification
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.ControlCommandEvent
import com.neogenesis.server.domain.model.DigitalTwinState
import com.neogenesis.server.domain.model.TelemetryEvent
import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.infrastructure.security.DataProtectionService
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import javax.sql.DataSource

private val json = Json { ignoreUnknownKeys = true }

class JdbcTelemetryEventStore(private val dataSource: DataSource) : TelemetryEventStore {
    override fun append(event: TelemetryEvent) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO telemetry_events(
                    printer_id,
                    timestamp_ms,
                    nozzle_temp_celsius,
                    extrusion_pressure_kpa,
                    cell_viability_index,
                    encrypted_image_matrix_base64,
                    source,
                    bio_ink_viscosity_index,
                    bio_ink_ph,
                    nir_ii_temp_celsius,
                    morphological_defect_probability,
                    print_job_id,
                    tissue_type,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, event.telemetry.printerId)
                statement.setLong(2, event.telemetry.timestampMs)
                statement.setFloat(3, event.telemetry.nozzleTempCelsius)
                statement.setFloat(4, event.telemetry.extrusionPressureKPa)
                statement.setFloat(5, event.telemetry.cellViabilityIndex)
                statement.setString(6, Base64.getEncoder().encodeToString(event.telemetry.encryptedImageMatrix))
                statement.setString(7, event.source)
                statement.setFloat(8, event.telemetry.bioInkViscosityIndex)
                statement.setFloat(9, event.telemetry.bioInkPh)
                statement.setFloat(10, event.telemetry.nirIiTempCelsius)
                statement.setFloat(11, event.telemetry.morphologicalDefectProbability)
                statement.setString(12, event.telemetry.printJobId)
                statement.setString(13, event.telemetry.tissueType)
                statement.setTimestamp(14, Timestamp.from(Instant.ofEpochMilli(event.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recent(limit: Int): List<TelemetryEvent> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    printer_id,
                    timestamp_ms,
                    nozzle_temp_celsius,
                    extrusion_pressure_kpa,
                    cell_viability_index,
                    encrypted_image_matrix_base64,
                    source,
                    bio_ink_viscosity_index,
                    bio_ink_ph,
                    nir_ii_temp_celsius,
                    morphological_defect_probability,
                    print_job_id,
                    tissue_type,
                    created_at
                FROM telemetry_events
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toTelemetryEvent())
                        }
                    }
                }
            }
        }
    }
}

class JdbcControlCommandStore(private val dataSource: DataSource) : ControlCommandStore {
    override fun append(event: ControlCommandEvent) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO control_commands(
                    command_id,
                    printer_id,
                    action_type,
                    adjust_pressure,
                    adjust_speed,
                    reason,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, event.command.commandId)
                statement.setString(2, event.command.printerId)
                statement.setString(3, event.command.actionType.name)
                statement.setFloat(4, event.command.adjustPressure)
                statement.setFloat(5, event.command.adjustSpeed)
                statement.setString(6, event.command.reason)
                statement.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(event.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recent(limit: Int): List<ControlCommandEvent> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    command_id,
                    printer_id,
                    action_type,
                    adjust_pressure,
                    adjust_speed,
                    reason,
                    created_at
                FROM control_commands
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                ControlCommandEvent(
                                    command =
                                        ControlCommand(
                                            commandId = rs.getString("command_id"),
                                            printerId = rs.getString("printer_id"),
                                            actionType = ControlActionType.valueOf(rs.getString("action_type")),
                                            adjustPressure = rs.getFloat("adjust_pressure"),
                                            adjustSpeed = rs.getFloat("adjust_speed"),
                                            reason = rs.getString("reason"),
                                        ),
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

class JdbcDigitalTwinStore(private val dataSource: DataSource) : DigitalTwinStore {
    override fun upsert(state: DigitalTwinState) {
        dataSource.connection.use { connection ->
            val updatedRows =
                connection.prepareStatement(
                    """
                    UPDATE digital_twin_snapshots
                    SET
                        updated_at_ms = ?,
                        current_viability = ?,
                        predicted_viability_5m = ?,
                        collapse_risk_score = ?,
                        recommended_action = ?,
                        confidence = ?
                    WHERE printer_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, state.updatedAtMs)
                    statement.setFloat(2, state.currentViability)
                    statement.setFloat(3, state.predictedViability5m)
                    statement.setFloat(4, state.collapseRiskScore)
                    statement.setString(5, state.recommendedAction.name)
                    statement.setFloat(6, state.confidence)
                    statement.setString(7, state.printerId)
                    statement.executeUpdate()
                }

            if (updatedRows == 0) {
                connection.prepareStatement(
                    """
                    INSERT INTO digital_twin_snapshots(
                        printer_id,
                        updated_at_ms,
                        current_viability,
                        predicted_viability_5m,
                        collapse_risk_score,
                        recommended_action,
                        confidence
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, state.printerId)
                    statement.setLong(2, state.updatedAtMs)
                    statement.setFloat(3, state.currentViability)
                    statement.setFloat(4, state.predictedViability5m)
                    statement.setFloat(5, state.collapseRiskScore)
                    statement.setString(6, state.recommendedAction.name)
                    statement.setFloat(7, state.confidence)
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun findByPrinterId(printerId: String): DigitalTwinState? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    printer_id,
                    updated_at_ms,
                    current_viability,
                    predicted_viability_5m,
                    collapse_risk_score,
                    recommended_action,
                    confidence
                FROM digital_twin_snapshots
                WHERE printer_id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, printerId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.toDigitalTwinState() else null
                }
            }
        }
    }

    override fun findAll(): List<DigitalTwinState> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    printer_id,
                    updated_at_ms,
                    current_viability,
                    predicted_viability_5m,
                    collapse_risk_score,
                    recommended_action,
                    confidence
                FROM digital_twin_snapshots
                ORDER BY updated_at_ms DESC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toDigitalTwinState())
                        }
                    }
                }
            }
        }
    }
}

class JdbcClinicalDocumentStore(
    private val dataSource: DataSource,
    private val dataProtectionService: DataProtectionService? = null,
    private val defaultRetentionDays: Int = 3650,
) : ClinicalDocumentStore {
    override fun append(document: ClinicalDocument) {
        val classification = document.metadata["dataClassification"] ?: "PHI"
        val protectedPayload =
            dataProtectionService?.protect(document.content, classification)
                ?: com.neogenesis.server.infrastructure.security.ProtectedPayload(document.content, null)
        val retentionUntil =
            Instant.ofEpochMilli(document.createdAtMs)
                .plusSeconds(defaultRetentionDays.toLong() * 24L * 3600L)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO clinical_documents(
                    document_type,
                    external_id,
                    patient_id,
                    content,
                    metadata_json,
                    data_classification,
                    key_id,
                    retention_until,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, document.documentType.name)
                statement.setString(2, document.externalId)
                statement.setString(3, document.patientId)
                statement.setString(4, protectedPayload.content)
                statement.setString(5, encodeMap(document.metadata))
                statement.setString(6, classification)
                statement.setString(7, protectedPayload.keyId)
                statement.setTimestamp(8, Timestamp.from(retentionUntil))
                statement.setTimestamp(9, Timestamp.from(Instant.ofEpochMilli(document.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recent(limit: Int): List<ClinicalDocument> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    document_type,
                    external_id,
                    patient_id,
                    content,
                    metadata_json,
                    created_at
                FROM clinical_documents
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val raw = rs.toClinicalDocument()
                            add(
                                raw.copy(
                                    content = dataProtectionService?.unprotect(raw.content) ?: raw.content,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun findByPatientId(
        patientId: String,
        limit: Int,
    ): List<ClinicalDocument> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    document_type,
                    external_id,
                    patient_id,
                    content,
                    metadata_json,
                    created_at
                FROM clinical_documents
                WHERE patient_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, patientId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val raw = rs.toClinicalDocument()
                            add(
                                raw.copy(
                                    content = dataProtectionService?.unprotect(raw.content) ?: raw.content,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class JdbcAuditEventStore(private val dataSource: DataSource) : AuditEventStore {
    override fun append(event: AuditEvent) {
        dataSource.connection.use { connection ->
            val previousHash =
                connection.prepareStatement(
                    """
                    SELECT event_hash
                    FROM audit_events
                    ORDER BY id DESC
                    LIMIT 1
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("event_hash") else null
                    }
                }
            val eventHash = computeAuditHash(previousHash, event)

            connection.prepareStatement(
                """
                INSERT INTO audit_events(
                    actor,
                    action,
                    resource_type,
                    resource_id,
                    outcome,
                    requirement_ids,
                    details_json,
                    previous_hash,
                    event_hash,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, event.actor)
                statement.setString(2, event.action)
                statement.setString(3, event.resourceType)
                statement.setString(4, event.resourceId)
                statement.setString(5, event.outcome)
                statement.setString(6, event.requirementIds.joinToString(","))
                statement.setString(7, encodeMap(event.details))
                statement.setString(8, previousHash)
                statement.setString(9, eventHash)
                statement.setTimestamp(10, Timestamp.from(Instant.ofEpochMilli(event.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recent(limit: Int): List<AuditEvent> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    actor,
                    action,
                    resource_type,
                    resource_id,
                    outcome,
                    requirement_ids,
                    details_json,
                    created_at
                FROM audit_events
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                AuditEvent(
                                    actor = rs.getString("actor"),
                                    action = rs.getString("action"),
                                    resourceType = rs.getString("resource_type"),
                                    resourceId = rs.getString("resource_id"),
                                    outcome = rs.getString("outcome"),
                                    requirementIds =
                                        rs.getString("requirement_ids")
                                            .split(',')
                                            .map { it.trim() }
                                            .filter { it.isNotBlank() },
                                    details = decodeMap(rs.getString("details_json")),
                                    createdAtMs = rs.getTimestamp("created_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun verifyChain(limit: Int): AuditChainVerification {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    actor,
                    action,
                    resource_type,
                    resource_id,
                    outcome,
                    requirement_ids,
                    details_json,
                    previous_hash,
                    event_hash,
                    created_at
                FROM audit_events
                ORDER BY id ASC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    var index = 0
                    var previousHash: String? = null
                    while (rs.next()) {
                        index++
                        val event =
                            AuditEvent(
                                actor = rs.getString("actor"),
                                action = rs.getString("action"),
                                resourceType = rs.getString("resource_type"),
                                resourceId = rs.getString("resource_id"),
                                outcome = rs.getString("outcome"),
                                requirementIds =
                                    rs.getString("requirement_ids")
                                        .split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() },
                                details = decodeMap(rs.getString("details_json")),
                                createdAtMs = rs.getTimestamp("created_at").time,
                            )
                        val storedPrevious = rs.getString("previous_hash")
                        val storedHash = rs.getString("event_hash")

                        if (storedPrevious != previousHash) {
                            return AuditChainVerification(
                                valid = false,
                                checkedEvents = index,
                                failureIndex = index,
                                failureReason = "previous hash mismatch",
                            )
                        }

                        val computed = computeAuditHash(previousHash, event)
                        if (computed != storedHash) {
                            return AuditChainVerification(
                                valid = false,
                                checkedEvents = index,
                                failureIndex = index,
                                failureReason = "event hash mismatch",
                            )
                        }
                        previousHash = storedHash
                    }
                    return AuditChainVerification(
                        valid = true,
                        checkedEvents = index,
                        failureIndex = null,
                        failureReason = null,
                    )
                }
            }
        }
    }
}

private fun ResultSet.toTelemetryEvent(): TelemetryEvent {
    val encodedImage = getString("encrypted_image_matrix_base64") ?: ""
    return TelemetryEvent(
        telemetry =
            TelemetryState(
                printerId = getString("printer_id"),
                timestampMs = getLong("timestamp_ms"),
                nozzleTempCelsius = getFloat("nozzle_temp_celsius"),
                extrusionPressureKPa = getFloat("extrusion_pressure_kpa"),
                cellViabilityIndex = getFloat("cell_viability_index"),
                encryptedImageMatrix = if (encodedImage.isBlank()) byteArrayOf() else Base64.getDecoder().decode(encodedImage),
                bioInkViscosityIndex = getFloatSafely("bio_ink_viscosity_index", 0.0f),
                bioInkPh = getFloatSafely("bio_ink_ph", 7.4f),
                nirIiTempCelsius = getFloatSafely("nir_ii_temp_celsius", 37.0f),
                morphologicalDefectProbability = getFloatSafely("morphological_defect_probability", 0.0f),
                printJobId = getStringSafely("print_job_id", ""),
                tissueType = getStringSafely("tissue_type", "retina"),
            ),
        source = getString("source"),
        createdAtMs = getTimestamp("created_at").time,
    )
}

private fun ResultSet.toDigitalTwinState(): DigitalTwinState {
    return DigitalTwinState(
        printerId = getString("printer_id"),
        updatedAtMs = getLong("updated_at_ms"),
        currentViability = getFloat("current_viability"),
        predictedViability5m = getFloat("predicted_viability_5m"),
        collapseRiskScore = getFloat("collapse_risk_score"),
        recommendedAction = ControlActionType.valueOf(getString("recommended_action")),
        confidence = getFloat("confidence"),
    )
}

private fun ResultSet.toClinicalDocument(): ClinicalDocument {
    return ClinicalDocument(
        documentType = com.neogenesis.server.domain.model.ClinicalDocumentType.valueOf(getString("document_type")),
        externalId = getString("external_id"),
        patientId = getString("patient_id"),
        content = getString("content"),
        metadata = decodeMap(getString("metadata_json")),
        createdAtMs = getTimestamp("created_at").time,
    )
}

private fun encodeMap(value: Map<String, String>): String {
    return json.encodeToString(MapSerializer(String.serializer(), String.serializer()), value)
}

private fun decodeMap(value: String?): Map<String, String> {
    if (value.isNullOrBlank()) {
        return emptyMap()
    }
    return runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)
    }.getOrElse {
        emptyMap()
    }
}

private fun computeAuditHash(
    previousHash: String?,
    event: AuditEvent,
): String {
    val canonical =
        buildString {
            append(previousHash.orEmpty())
            append('|')
            append(event.actor)
            append('|')
            append(event.action)
            append('|')
            append(event.resourceType)
            append('|')
            append(event.resourceId.orEmpty())
            append('|')
            append(event.outcome)
            append('|')
            append(event.requirementIds.joinToString(","))
            append('|')
            append(event.details.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" })
            append('|')
            append(event.createdAtMs)
        }
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

private fun ResultSet.getFloatSafely(
    column: String,
    fallback: Float,
): Float {
    return runCatching { getFloat(column) }.getOrElse { fallback }
}

private fun ResultSet.getStringSafely(
    column: String,
    fallback: String,
): String {
    return runCatching { getString(column) ?: fallback }.getOrElse { fallback }
}
