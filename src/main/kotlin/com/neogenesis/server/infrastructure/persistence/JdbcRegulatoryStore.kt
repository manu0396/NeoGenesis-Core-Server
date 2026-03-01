package com.neogenesis.server.infrastructure.persistence

import com.neogenesis.server.application.port.RegulatoryStore
import com.neogenesis.server.domain.model.CapaRecord
import com.neogenesis.server.domain.model.CapaStatus
import com.neogenesis.server.domain.model.DhfArtifact
import com.neogenesis.server.domain.model.RiskRecord
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcRegulatoryStore(
    private val dataSource: DataSource,
) : RegulatoryStore {
    override fun createCapa(record: CapaRecord): CapaRecord {
        val tenantId = record.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO capa_records(
                    tenant_id,
                    title,
                    description,
                    requirement_id,
                    owner,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, record.title)
                statement.setString(3, record.description)
                statement.setString(4, record.requirementId)
                statement.setString(5, record.owner)
                statement.setString(6, record.status.name)
                statement.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(record.createdAtMs)))
                statement.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(record.updatedAtMs)))
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    val id = if (keys.next()) keys.getLong(1) else null
                    return record.copy(id = id)
                }
            }
        }
    }

    override fun listCapas(
        tenantId: String,
        limit: Int,
    ): List<CapaRecord> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    tenant_id,
                    title,
                    description,
                    requirement_id,
                    owner,
                    status,
                    created_at,
                    updated_at
                FROM capa_records
                WHERE tenant_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toCapaRecord())
                        }
                    }
                }
            }
        }
    }

    override fun updateCapaStatus(
        tenantId: String,
        capaId: Long,
        status: String,
        updatedAtMs: Long,
    ): Boolean {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                UPDATE capa_records
                SET status = ?, updated_at = ?
                WHERE tenant_id = ? AND id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, status)
                statement.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(updatedAtMs)))
                statement.setString(3, tenantId)
                statement.setLong(4, capaId)
                statement.executeUpdate() > 0
            }
        }
    }

    override fun upsertRisk(record: RiskRecord) {
        val tenantId = record.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            val updated =
                connection.prepareStatement(
                    """
                    UPDATE risk_register
                    SET
                        hazard_description = ?,
                        severity = ?,
                        probability = ?,
                        detectability = ?,
                        controls = ?,
                        residual_risk_level = ?,
                        linked_requirement_id = ?,
                        owner = ?,
                        updated_at = ?
                    WHERE tenant_id = ? AND risk_id = ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, record.hazardDescription)
                    statement.setInt(2, record.severity)
                    statement.setInt(3, record.probability)
                    statement.setInt(4, record.detectability)
                    statement.setString(5, record.controls)
                    statement.setInt(6, record.residualRiskLevel)
                    statement.setString(7, record.linkedRequirementId)
                    statement.setString(8, record.owner)
                    statement.setTimestamp(9, Timestamp.from(Instant.ofEpochMilli(record.updatedAtMs)))
                    statement.setString(10, tenantId)
                    statement.setString(11, record.riskId)
                    statement.executeUpdate()
                }

            if (updated == 0) {
                connection.prepareStatement(
                    """
                    INSERT INTO risk_register(
                        tenant_id,
                        risk_id,
                        hazard_description,
                        severity,
                        probability,
                        detectability,
                        controls,
                        residual_risk_level,
                        linked_requirement_id,
                        owner,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, tenantId)
                    statement.setString(2, record.riskId)
                    statement.setString(3, record.hazardDescription)
                    statement.setInt(4, record.severity)
                    statement.setInt(5, record.probability)
                    statement.setInt(6, record.detectability)
                    statement.setString(7, record.controls)
                    statement.setInt(8, record.residualRiskLevel)
                    statement.setString(9, record.linkedRequirementId)
                    statement.setString(10, record.owner)
                    statement.setTimestamp(11, Timestamp.from(Instant.ofEpochMilli(record.createdAtMs)))
                    statement.setTimestamp(12, Timestamp.from(Instant.ofEpochMilli(record.updatedAtMs)))
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun listRisks(
        tenantId: String,
        limit: Int,
    ): List<RiskRecord> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    tenant_id,
                    risk_id,
                    hazard_description,
                    severity,
                    probability,
                    detectability,
                    controls,
                    residual_risk_level,
                    linked_requirement_id,
                    owner,
                    created_at,
                    updated_at
                FROM risk_register
                WHERE tenant_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toRiskRecord())
                        }
                    }
                }
            }
        }
    }

    override fun addDhfArtifact(artifact: DhfArtifact): DhfArtifact {
        val tenantId = artifact.tenantId
        dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                INSERT INTO dhf_artifacts(
                    tenant_id,
                    artifact_type,
                    artifact_name,
                    version,
                    location,
                    checksum_sha256,
                    approved_by,
                    approved_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setString(2, artifact.artifactType)
                statement.setString(3, artifact.artifactName)
                statement.setString(4, artifact.version)
                statement.setString(5, artifact.location)
                statement.setString(6, artifact.checksumSha256)
                statement.setString(7, artifact.approvedBy)
                statement.setTimestamp(8, Timestamp.from(Instant.ofEpochMilli(artifact.approvedAtMs)))
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    val id = if (keys.next()) keys.getLong(1) else null
                    return artifact.copy(id = id)
                }
            }
        }
    }

    override fun listDhfArtifacts(
        tenantId: String,
        limit: Int,
    ): List<DhfArtifact> {
        return dataSource.useTenantConnection(tenantId) { connection ->
            connection.prepareStatement(
                """
                SELECT
                    id,
                    tenant_id,
                    artifact_type,
                    artifact_name,
                    version,
                    location,
                    checksum_sha256,
                    approved_by,
                    approved_at
                FROM dhf_artifacts
                WHERE tenant_id = ?
                ORDER BY approved_at DESC
                LIMIT ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, tenantId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toDhfArtifact())
                        }
                    }
                }
            }
        }
    }
}

private fun ResultSet.toCapaRecord(): CapaRecord {
    return CapaRecord(
        tenantId = getString("tenant_id"),
        id = getLong("id"),
        title = getString("title"),
        description = getString("description"),
        requirementId = getString("requirement_id"),
        owner = getString("owner"),
        status = CapaStatus.valueOf(getString("status")),
        createdAtMs = getTimestamp("created_at").time,
        updatedAtMs = getTimestamp("updated_at").time,
    )
}

private fun ResultSet.toRiskRecord(): RiskRecord {
    return RiskRecord(
        tenantId = getString("tenant_id"),
        riskId = getString("risk_id"),
        hazardDescription = getString("hazard_description"),
        severity = getInt("severity"),
        probability = getInt("probability"),
        detectability = getInt("detectability"),
        controls = getString("controls"),
        residualRiskLevel = getInt("residual_risk_level"),
        linkedRequirementId = getString("linked_requirement_id"),
        owner = getString("owner"),
        createdAtMs = getTimestamp("created_at").time,
        updatedAtMs = getTimestamp("updated_at").time,
    )
}

private fun ResultSet.toDhfArtifact(): DhfArtifact {
    return DhfArtifact(
        tenantId = getString("tenant_id"),
        id = getLong("id"),
        artifactType = getString("artifact_type"),
        artifactName = getString("artifact_name"),
        version = getString("version"),
        location = getString("location"),
        checksumSha256 = getString("checksum_sha256"),
        approvedBy = getString("approved_by"),
        approvedAtMs = getTimestamp("approved_at").time,
    )
}
