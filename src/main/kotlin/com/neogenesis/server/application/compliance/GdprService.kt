package com.neogenesis.server.application.compliance

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.GdprStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.ConsentStatus
import com.neogenesis.server.domain.model.GdprConsentRecord
import com.neogenesis.server.domain.model.GdprErasureRecord
import com.neogenesis.server.infrastructure.config.AppConfig

class GdprService(
    private val gdprStore: GdprStore,
    private val auditTrailService: AuditTrailService,
    private val gdprConfig: AppConfig.GdprConfig,
) {
    fun grantConsent(
        tenantId: String,
        patientId: String,
        purpose: String,
        legalBasis: String,
        actor: String,
    ): GdprConsentRecord {
        val record =
            GdprConsentRecord(
                tenantId = tenantId,
                patientId = patientId,
                purpose = purpose,
                status = ConsentStatus.GRANTED,
                legalBasis = legalBasis,
                grantedBy = actor,
            )
        gdprStore.appendConsent(record)
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "gdpr.consent.grant",
                resourceType = "patient",
                resourceId = patientId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-009", "REQ-ISO-012"),
                details = mapOf("purpose" to purpose, "legalBasis" to legalBasis),
            ),
        )
        return record
    }

    fun revokeConsent(
        tenantId: String,
        patientId: String,
        purpose: String,
        legalBasis: String,
        actor: String,
    ): GdprConsentRecord {
        val record =
            GdprConsentRecord(
                tenantId = tenantId,
                patientId = patientId,
                purpose = purpose,
                status = ConsentStatus.REVOKED,
                legalBasis = legalBasis,
                grantedBy = actor,
            )
        gdprStore.appendConsent(record)
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "gdpr.consent.revoke",
                resourceType = "patient",
                resourceId = patientId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-009", "REQ-ISO-012"),
                details = mapOf("purpose" to purpose, "legalBasis" to legalBasis),
            ),
        )
        return record
    }

    fun hasActiveConsent(
        tenantId: String,
        patientId: String,
        purpose: String,
    ): Boolean {
        if (!gdprConfig.enforceConsent) {
            return true
        }
        val latest = gdprStore.latestConsent(tenantId, patientId, purpose) ?: return false
        return latest.status == ConsentStatus.GRANTED
    }

    fun recordErasure(
        tenantId: String,
        patientId: String,
        reason: String,
        actor: String,
    ): GdprErasureRecord {
        val affected = gdprStore.anonymizeClinicalDocuments(tenantId, patientId)
        val record =
            GdprErasureRecord(
                tenantId = tenantId,
                patientId = patientId,
                requestedBy = actor,
                reason = reason,
                outcome = "success",
                affectedRows = affected,
            )
        gdprStore.appendErasure(record)
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "gdpr.erasure.execute",
                resourceType = "patient",
                resourceId = patientId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-009", "REQ-ISO-012"),
                details = mapOf("reason" to reason, "affectedRows" to affected.toString()),
            ),
        )
        return record
    }

    fun recentErasures(
        tenantId: String,
        limit: Int,
    ): List<GdprErasureRecord> = gdprStore.recentErasures(tenantId, limit)

    fun enforceRetention(
        tenantId: String,
        actor: String,
    ): Int {
        val affected = gdprStore.anonymizeExpiredClinicalDocuments(tenantId)
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "gdpr.retention.enforce",
                resourceType = "clinical_document",
                resourceId = null,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-012"),
                details = mapOf("affectedRows" to affected.toString()),
            ),
        )
        return affected
    }
}
