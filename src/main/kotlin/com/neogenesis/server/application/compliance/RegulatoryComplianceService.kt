package com.neogenesis.server.application.compliance

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.RegulatoryStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.CapaRecord
import com.neogenesis.server.domain.model.CapaStatus
import com.neogenesis.server.domain.model.DhfArtifact
import com.neogenesis.server.domain.model.RiskRecord
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService

class RegulatoryComplianceService(
    private val regulatoryStore: RegulatoryStore,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService,
) {
    fun createCapa(
        tenantId: String,
        title: String,
        description: String,
        requirementId: String,
        owner: String,
        actor: String,
    ): CapaRecord {
        val created =
            regulatoryStore.createCapa(
                CapaRecord(
                    tenantId = tenantId,
                    title = title,
                    description = description,
                    requirementId = requirementId,
                    owner = owner,
                ),
            )
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "regulatory.capa.create",
                resourceType = "capa_record",
                resourceId = created.id?.toString(),
                outcome = "success",
                requirementIds = listOf("REQ-ISO-008"),
                details =
                    mapOf(
                        "requirementId" to requirementId,
                        "owner" to owner,
                    ),
            ),
        )
        metricsService.recordAuditEvent("regulatory.capa.create", "success")
        return created
    }

    fun updateCapaStatus(
        tenantId: String,
        capaId: Long,
        status: CapaStatus,
        actor: String,
    ): Boolean {
        val updated = regulatoryStore.updateCapaStatus(tenantId, capaId, status.name, System.currentTimeMillis())
        if (updated) {
            auditTrailService.record(
                AuditEvent(
                    tenantId = tenantId,
                    actor = actor,
                    action = "regulatory.capa.update",
                    resourceType = "capa_record",
                    resourceId = capaId.toString(),
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-008"),
                    details = mapOf("status" to status.name),
                ),
            )
        }
        return updated
    }

    fun listCapas(tenantId: String, limit: Int): List<CapaRecord> = regulatoryStore.listCapas(tenantId, limit)

    fun upsertRisk(
        tenantId: String,
        record: RiskRecord,
        actor: String,
    ) {
        regulatoryStore.upsertRisk(record.copy(tenantId = tenantId))
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "regulatory.risk.upsert",
                resourceType = "risk_register",
                resourceId = record.riskId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-002", "REQ-ISO-007"),
                details =
                    mapOf(
                        "severity" to record.severity.toString(),
                        "probability" to record.probability.toString(),
                        "residualRiskLevel" to record.residualRiskLevel.toString(),
                    ),
            ),
        )
    }

    fun listRisks(tenantId: String, limit: Int): List<RiskRecord> = regulatoryStore.listRisks(tenantId, limit)

    fun addDhfArtifact(
        tenantId: String,
        artifactType: String,
        artifactName: String,
        version: String,
        location: String,
        checksumSha256: String,
        approvedBy: String,
        actor: String,
    ): DhfArtifact {
        val artifact =
            regulatoryStore.addDhfArtifact(
                DhfArtifact(
                    tenantId = tenantId,
                    artifactType = artifactType,
                    artifactName = artifactName,
                    version = version,
                    location = location,
                    checksumSha256 = checksumSha256,
                    approvedBy = approvedBy,
                ),
            )
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "regulatory.dhf.add",
                resourceType = "dhf_artifact",
                resourceId = artifact.id?.toString(),
                outcome = "success",
                requirementIds = listOf("REQ-ISO-001", "REQ-ISO-002"),
                details =
                    mapOf(
                        "artifactType" to artifactType,
                        "version" to version,
                    ),
            ),
        )
        return artifact
    }

    fun listDhfArtifacts(tenantId: String, limit: Int): List<DhfArtifact> = regulatoryStore.listDhfArtifacts(tenantId, limit)
}
