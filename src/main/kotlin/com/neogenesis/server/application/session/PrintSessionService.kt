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
    private val metricsService: OperationalMetricsService,
) {
    fun create(
        tenantId: String,
        printerId: String,
        planId: String,
        patientId: String,
        actor: String,
    ): PrintSession {
        val session =
            PrintSession(
                tenantId = tenantId,
                sessionId = "print-session-${UUID.randomUUID()}",
                printerId = printerId,
                planId = planId,
                patientId = patientId,
                status = PrintSessionStatus.CREATED,
            )

        printSessionStore.create(session)
        audit(tenantId, actor, "print.session.create", session.sessionId, session.printerId, session.status)
        metricsService.recordSessionStatusTransition(session.status.name)
        return session
    }

    fun activate(
        tenantId: String,
        sessionId: String,
        actor: String,
    ): PrintSession? {
        val session = printSessionStore.findBySessionId(tenantId, sessionId) ?: return null

        val active = printSessionStore.findActiveByPrinterId(tenantId, session.printerId)
        if (active != null && active.sessionId != sessionId) {
            printSessionStore.updateStatus(tenantId, active.sessionId, PrintSessionStatus.PAUSED, System.currentTimeMillis())
        }

        printSessionStore.updateStatus(tenantId, sessionId, PrintSessionStatus.ACTIVE, System.currentTimeMillis())
        val updated = printSessionStore.findBySessionId(tenantId, sessionId)
        if (updated != null) {
            audit(tenantId, actor, "print.session.activate", updated.sessionId, updated.printerId, updated.status)
            metricsService.recordSessionStatusTransition(updated.status.name)
        }
        return updated
    }

    fun complete(
        tenantId: String,
        sessionId: String,
        actor: String,
    ): PrintSession? {
        return updateStatus(tenantId, sessionId, PrintSessionStatus.COMPLETED, actor, "print.session.complete")
    }

    fun abort(
        tenantId: String,
        sessionId: String,
        actor: String,
    ): PrintSession? {
        return updateStatus(tenantId, sessionId, PrintSessionStatus.ABORTED, actor, "print.session.abort")
    }

    fun findActiveByPrinterId(
        tenantId: String,
        printerId: String,
    ): PrintSession? = printSessionStore.findActiveByPrinterId(tenantId, printerId)

    fun findActive(
        tenantId: String,
        limit: Int = 100,
    ): List<PrintSession> = printSessionStore.findActive(tenantId, limit)

    private fun updateStatus(
        tenantId: String,
        sessionId: String,
        status: PrintSessionStatus,
        actor: String,
        action: String,
    ): PrintSession? {
        val session = printSessionStore.findBySessionId(tenantId, sessionId) ?: return null
        printSessionStore.updateStatus(tenantId, session.sessionId, status, System.currentTimeMillis())
        val updated = printSessionStore.findBySessionId(tenantId, sessionId)
        if (updated != null) {
            audit(tenantId, actor, action, updated.sessionId, updated.printerId, updated.status)
            metricsService.recordSessionStatusTransition(updated.status.name)
        }
        return updated
    }

    private fun audit(
        tenantId: String,
        actor: String,
        action: String,
        sessionId: String,
        printerId: String,
        status: PrintSessionStatus,
    ) {
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = action,
                resourceType = "print_session",
                resourceId = sessionId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-002", "REQ-ISO-005"),
                details =
                    mapOf(
                        "printerId" to printerId,
                        "status" to status.name,
                    ),
            ),
        )
    }
}
