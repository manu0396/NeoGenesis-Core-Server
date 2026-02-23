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

    fun recent(limit: Int = 100): List<TelemetryEvent>
}

interface ControlCommandStore {
    fun append(event: ControlCommandEvent)

    fun recent(limit: Int = 100): List<ControlCommandEvent>
}

interface DigitalTwinStore {
    fun upsert(state: DigitalTwinState)

    fun findByPrinterId(printerId: String): DigitalTwinState?

    fun findAll(): List<DigitalTwinState>
}

interface ClinicalDocumentStore {
    fun append(document: ClinicalDocument)

    fun recent(limit: Int = 100): List<ClinicalDocument>

    fun findByPatientId(
        patientId: String,
        limit: Int = 100,
    ): List<ClinicalDocument>
}

interface AuditEventStore {
    fun append(event: AuditEvent)

    fun recent(limit: Int = 200): List<AuditEvent>

    fun verifyChain(limit: Int = 10_000): AuditChainVerification
}

interface RetinalPlanStore {
    fun save(plan: RetinalPrintPlan)

    fun findByPlanId(planId: String): RetinalPrintPlan?

    fun findLatestByPatientId(patientId: String): RetinalPrintPlan?

    fun findRecent(limit: Int = 100): List<RetinalPrintPlan>
}

interface PrintSessionStore {
    fun create(session: PrintSession)

    fun updateStatus(
        sessionId: String,
        status: PrintSessionStatus,
        updatedAtMs: Long,
    )

    fun findBySessionId(sessionId: String): PrintSession?

    fun findActiveByPrinterId(printerId: String): PrintSession?

    fun findActive(limit: Int = 100): List<PrintSession>
}

interface LatencyBreachStore {
    fun append(event: LatencyBreachEvent)

    fun recent(limit: Int = 200): List<LatencyBreachEvent>
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
        patientId: String,
        purpose: String,
    ): GdprConsentRecord?

    fun appendErasure(record: GdprErasureRecord)

    fun recentErasures(limit: Int = 100): List<GdprErasureRecord>

    fun anonymizeClinicalDocuments(patientId: String): Int

    fun anonymizeExpiredClinicalDocuments(): Int
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

    fun listCapas(limit: Int = 100): List<CapaRecord>

    fun updateCapaStatus(
        capaId: Long,
        status: String,
        updatedAtMs: Long,
    ): Boolean

    fun upsertRisk(record: RiskRecord)

    fun listRisks(limit: Int = 100): List<RiskRecord>

    fun addDhfArtifact(artifact: DhfArtifact): DhfArtifact

    fun listDhfArtifacts(limit: Int = 100): List<DhfArtifact>
}
