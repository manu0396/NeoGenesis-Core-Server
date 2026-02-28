package com.neogenesis.server.application.port

import com.neogenesis.server.domain.model.AuditChainVerification
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.CapaRecord
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.domain.model.ControlCommandEvent
import com.neogenesis.server.domain.model.DeadLetterOutboxEvent
import com.neogenesis.server.domain.model.DhfArtifact
import com.neogenesis.server.domain.model.DigitalTwinState
import com.neogenesis.server.domain.model.GdprConsentRecord
import com.neogenesis.server.domain.model.GdprErasureRecord
import com.neogenesis.server.domain.model.LatencyBreachEvent
import com.neogenesis.server.domain.model.PrintSession
import com.neogenesis.server.domain.model.PrintSessionStatus
import com.neogenesis.server.domain.model.RetinalPrintPlan
import com.neogenesis.server.domain.model.RiskRecord
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import com.neogenesis.server.domain.model.TelemetryEvent

interface TelemetryEventStore {
    fun append(event: TelemetryEvent)

    fun recent(
        tenantId: String,
        limit: Int = 100,
    ): List<TelemetryEvent>
}

interface ControlCommandStore {
    fun append(event: ControlCommandEvent)

    fun recent(
        tenantId: String,
        limit: Int = 100,
    ): List<ControlCommandEvent>
}

interface DigitalTwinStore {
    fun upsert(state: DigitalTwinState)

    fun findByPrinterId(tenantId: String, printerId: String): DigitalTwinState?

    fun findAll(tenantId: String): List<DigitalTwinState>
}

interface ClinicalDocumentStore {
    fun append(document: ClinicalDocument)

    fun recent(tenantId: String, limit: Int = 100): List<ClinicalDocument>

    fun findByPatientId(
        tenantId: String,
        patientId: String,
        limit: Int = 100,
    ): List<ClinicalDocument>
}

interface AuditEventStore {
    fun append(event: AuditEvent)

    fun recent(tenantId: String, limit: Int = 200): List<AuditEvent>

    fun verifyChain(tenantId: String, limit: Int = 10_000): AuditChainVerification
}

interface RetinalPlanStore {
    fun save(plan: RetinalPrintPlan)

    fun findByPlanId(tenantId: String, planId: String): RetinalPrintPlan?

    fun findLatestByPatientId(tenantId: String, patientId: String): RetinalPrintPlan?

    fun findRecent(tenantId: String, limit: Int = 100): List<RetinalPrintPlan>
}

interface PrintSessionStore {
    fun create(session: PrintSession)

    fun updateStatus(
        tenantId: String,
        sessionId: String,
        status: PrintSessionStatus,
        updatedAtMs: Long,
    )

    fun findBySessionId(tenantId: String, sessionId: String): PrintSession?

    fun findActiveByPrinterId(tenantId: String, printerId: String): PrintSession?

    fun findActive(tenantId: String, limit: Int = 100): List<PrintSession>
}

interface LatencyBreachStore {
    fun append(event: LatencyBreachEvent)

    fun recent(
        tenantId: String,
        limit: Int = 200,
    ): List<LatencyBreachEvent>
}

interface OutboxEventStore {
    fun enqueue(
        eventType: String,
        partitionKey: String,
        payloadJson: String,
    )

    fun pending(limit: Int = 100): List<ServerlessOutboxEvent>

    fun claimPending(
        limit: Int = 100,
        processingTtlMs: Long = 300_000L,
    ): List<ServerlessOutboxEvent>

    fun markProcessed(eventId: Long)

    fun scheduleRetry(
        eventId: Long,
        nextAttemptAtMs: Long,
        failureReason: String,
    )

    fun moveToDeadLetter(
        eventId: Long,
        failureReason: String,
    )

    fun deadLetter(limit: Int = 100): List<DeadLetterOutboxEvent>

    fun replayDeadLetter(deadLetterId: Long): Boolean
}

interface GdprStore {
    fun appendConsent(record: GdprConsentRecord)

    fun latestConsent(
        tenantId: String,
        patientId: String,
        purpose: String,
    ): GdprConsentRecord?

    fun appendErasure(record: GdprErasureRecord)

    fun recentErasures(
        tenantId: String,
        limit: Int = 100,
    ): List<GdprErasureRecord>

    fun anonymizeClinicalDocuments(
        tenantId: String,
        patientId: String,
    ): Int

    fun anonymizeExpiredClinicalDocuments(tenantId: String): Int
}

interface RequestIdempotencyStore {
    fun remember(
        operation: String,
        key: String,
        payloadHash: String,
        ttlSeconds: Long,
    ): IdempotencyRememberResult
}

enum class IdempotencyRememberResult {
    STORED,
    DUPLICATE_MATCH,
    DUPLICATE_MISMATCH,
}

interface RegulatoryStore {
    fun createCapa(record: CapaRecord): CapaRecord

    fun listCapas(
        tenantId: String,
        limit: Int = 100,
    ): List<CapaRecord>

    fun updateCapaStatus(
        tenantId: String,
        capaId: Long,
        status: String,
        updatedAtMs: Long,
    ): Boolean

    fun upsertRisk(record: RiskRecord)

    fun listRisks(
        tenantId: String,
        limit: Int = 100,
    ): List<RiskRecord>

    fun addDhfArtifact(artifact: DhfArtifact): DhfArtifact

    fun listDhfArtifacts(
        tenantId: String,
        limit: Int = 100,
    ): List<DhfArtifact>
}
