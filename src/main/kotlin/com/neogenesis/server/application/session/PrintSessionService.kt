package com.neogenesis.server.application.session

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.PrintSessionStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.PrintSession
import com.neogenesis.server.domain.model.PrintSessionStatus
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.util.UUID

class PrintSessionService(
    private val printSessionStore: PrintSessionStore,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService
) {

    fun create(printerId: String, planId: String, patientId: String, actor: String): PrintSession {
        val session = PrintSession(
            sessionId = "print-session-${UUID.randomUUID()}",
            printerId = printerId,
            planId = planId,
            patientId = patientId,
            status = PrintSessionStatus.CREATED
        )

        printSessionStore.create(session)
        audit(actor, "print.session.create", session.sessionId, session.printerId, session.status)
        metricsService.recordSessionStatusTransition(session.status.name)
        return session
    }

    fun activate(sessionId: String, actor: String): PrintSession? {
        val session = printSessionStore.findBySessionId(sessionId) ?: return null

        val active = printSessionStore.findActiveByPrinterId(session.printerId)
        if (active != null && active.sessionId != sessionId) {
            printSessionStore.updateStatus(active.sessionId, PrintSessionStatus.PAUSED, System.currentTimeMillis())
        }

        printSessionStore.updateStatus(sessionId, PrintSessionStatus.ACTIVE, System.currentTimeMillis())
        val updated = printSessionStore.findBySessionId(sessionId)
        if (updated != null) {
            audit(actor, "print.session.activate", updated.sessionId, updated.printerId, updated.status)
            metricsService.recordSessionStatusTransition(updated.status.name)
        }
        return updated
    }

    fun complete(sessionId: String, actor: String): PrintSession? {
        return updateStatus(sessionId, PrintSessionStatus.COMPLETED, actor, "print.session.complete")
    }

    fun abort(sessionId: String, actor: String): PrintSession? {
        return updateStatus(sessionId, PrintSessionStatus.ABORTED, actor, "print.session.abort")
    }

    fun findActiveByPrinterId(printerId: String): PrintSession? = printSessionStore.findActiveByPrinterId(printerId)

    fun findActive(limit: Int = 100): List<PrintSession> = printSessionStore.findActive(limit)

    private fun updateStatus(
        sessionId: String,
        status: PrintSessionStatus,
        actor: String,
        action: String
    ): PrintSession? {
        val session = printSessionStore.findBySessionId(sessionId) ?: return null
        printSessionStore.updateStatus(session.sessionId, status, System.currentTimeMillis())
        val updated = printSessionStore.findBySessionId(sessionId)
        if (updated != null) {
            audit(actor, action, updated.sessionId, updated.printerId, updated.status)
            metricsService.recordSessionStatusTransition(updated.status.name)
        }
        return updated
    }

    private fun audit(actor: String, action: String, sessionId: String, printerId: String, status: PrintSessionStatus) {
        auditTrailService.record(
            AuditEvent(
                actor = actor,
                action = action,
                resourceType = "print_session",
                resourceId = sessionId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-002", "REQ-ISO-005"),
                details = mapOf(
                    "printerId" to printerId,
                    "status" to status.name
                )
            )
        )
    }
}
