package com.neogenesis.server.application

import com.neogenesis.server.application.port.AuditEventStore
import com.neogenesis.server.domain.model.AuditChainVerification
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService

class AuditTrailService(
    private val auditEventStore: AuditEventStore,
    private val metricsService: OperationalMetricsService
) {
    fun record(event: AuditEvent) {
        auditEventStore.append(event)
        metricsService.recordAuditEvent(event.action, event.outcome)
    }

    fun recent(limit: Int = 200): List<AuditEvent> = auditEventStore.recent(limit)

    fun verifyChain(limit: Int = 10_000): AuditChainVerification = auditEventStore.verifyChain(limit)
}
