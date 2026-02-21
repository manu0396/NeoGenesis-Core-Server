package com.neogenesis.server.application.sre

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.LatencyBreachStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.LatencyBreachEvent
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService

class LatencyBudgetService(
    private val thresholdMs: Long,
    private val latencyBreachStore: LatencyBreachStore,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService
) {

    fun recordIfBreached(printerId: String, source: String, durationNanos: Long) {
        val durationMs = durationNanos / 1_000_000.0
        if (durationMs <= thresholdMs) {
            return
        }

        val event = LatencyBreachEvent(
            printerId = printerId,
            source = source,
            durationMs = durationMs,
            thresholdMs = thresholdMs
        )
        latencyBreachStore.append(event)
        metricsService.recordLatencyBudgetBreach(source)

        auditTrailService.record(
            AuditEvent(
                actor = "latency-monitor",
                action = "sre.latency.breach",
                resourceType = "printer",
                resourceId = printerId,
                outcome = "warning",
                requirementIds = listOf("REQ-ISO-006"),
                details = mapOf(
                    "source" to source,
                    "durationMs" to "%.3f".format(durationMs),
                    "thresholdMs" to thresholdMs.toString()
                )
            )
        )
    }

    fun recentBreaches(limit: Int = 200): List<LatencyBreachEvent> = latencyBreachStore.recent(limit)
}
