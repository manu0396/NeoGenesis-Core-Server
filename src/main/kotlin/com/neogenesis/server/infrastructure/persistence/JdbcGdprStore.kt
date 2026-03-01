package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.port.GdprStore
import com.neogenesis.server.domain.model.ConsentStatus
import com.neogenesis.server.domain.model.GdprConsentRecord
import com.neogenesis.server.domain.model.GdprErasureRecord
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcGdprStore(private val dataSource: DataSource) : GdprStore {
    override fun appendConsent(record: GdprConsentRecord) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO gdpr_consents(
                    patient_id,
                    purpose,
                    status,
                    legal_basis,
                    granted_by,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, record.patientId)
                statement.setString(2, record.purpose)
                statement.setString(3, record.status.name)
                statement.setString(4, record.legalBasis)
                statement.setString(5, record.grantedBy)
                statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(record.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun latestConsent(
        patientId: String,
        purpose: String,
    ): GdprConsentRecord? {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    patient_id,
                    purpose,
                    status,
                    legal_basis,
                    granted_by,
                    created_at
                FROM gdpr_consents
                WHERE patient_id = ?
                  AND purpose = ?
                ORDER BY created_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, patientId)
                statement.setString(2, purpose)
                statement.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        GdprConsentRecord(
                            id = rs.getLong("id"),
                            patientId = rs.getString("patient_id"),
                            purpose = rs.getString("purpose"),
                            status = ConsentStatus.valueOf(rs.getString("status")),
                            legalBasis = rs.getString("legal_basis"),
                            grantedBy = rs.getString("granted_by"),
                            createdAtMs = rs.getTimestamp("created_at").time,
                        )
                    }
                }
            }
        }
    }

    override fun appendErasure(record: GdprErasureRecord) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO gdpr_erasure_requests(
                    patient_id,
                    requested_by,
                    reason,
                    outcome,
                    affected_rows,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, record.patientId)
                statement.setString(2, record.requestedBy)
                statement.setString(3, record.reason)
                statement.setString(4, record.outcome)
                statement.setInt(5, record.affectedRows)
                statement.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(record.createdAtMs)))
                statement.executeUpdate()
            }
        }
    }

    override fun recentErasures(limit: Int): List<GdprErasureRecord> {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    patient_id,
                    requested_by,
                    reason,
                    outcome,
                    affected_rows,
                    created_at
                FROM gdpr_erasure_requests
                ORDER BY created_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                GdprErasureRecord(
                                    id = rs.getLong("id"),
                                    patientId = rs.getString("patient_id"),
                                    requestedBy = rs.getString("requested_by"),
                                    reason = rs.getString("reason"),
                                    outcome = rs.getString("outcome"),
                                    affectedRows = rs.getInt("affected_rows"),
                                    createdAtMs = rs.getTimestamp("created_at").time,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun anonymizeClinicalDocuments(patientId: String): Int {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE clinical_documents
                SET
                    content = '[ANONYMIZED]',
                    patient_id = NULL,
                    anonymized_at = CURRENT_TIMESTAMP
                WHERE patient_id = ?
                  AND anonymized_at IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, patientId)
                statement.executeUpdate()
            }
        }
    }

    override fun anonymizeExpiredClinicalDocuments(): Int {
        return dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE clinical_documents
                SET
                    content = '[ANONYMIZED_RETENTION]',
                    patient_id = NULL,
                    anonymized_at = CURRENT_TIMESTAMP
                WHERE anonymized_at IS NULL
                  AND retention_until IS NOT NULL
                  AND retention_until < CURRENT_TIMESTAMP
                """.trimIndent(),
            ).use { statement ->
                statement.executeUpdate()
            }
        }
    }
}
