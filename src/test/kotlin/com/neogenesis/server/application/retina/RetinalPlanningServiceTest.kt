package com.neogenesis.server.application.retina

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.AuditEventStore
import com.neogenesis.server.application.port.RetinalPlanStore
import com.neogenesis.server.domain.model.AuditChainVerification
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.RetinalPrintPlan
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RetinalPlanningServiceTest {
    @Test
    fun `creates retina plan from dicom metadata`() {
        val store = FakeRetinalPlanStore()
        val auditStore = FakeAuditStore()
        val metrics = OperationalMetricsService(SimpleMeterRegistry())
        val auditService = AuditTrailService(auditStore, metrics)
        val service = RetinalPlanningService(store, auditService, metrics)

        val plan =
            service.createPlanFromDicom(
                patientId = "patient-1",
                sourceDocumentId = "dicom-1",
                metadata = mapOf("layerCount" to "8", "retinalThicknessMicrons" to "300"),
                actor = "tester",
            )

        assertEquals("patient-1", plan.patientId)
        assertEquals(8, plan.layers.size)
        assertNotNull(store.findByPlanId(plan.planId))
    }

    private class FakeRetinalPlanStore : RetinalPlanStore {
        private val data = mutableMapOf<String, RetinalPrintPlan>()

        override fun save(plan: RetinalPrintPlan) {
            data[plan.planId] = plan
        }

        override fun findByPlanId(planId: String): RetinalPrintPlan? = data[planId]

        override fun findLatestByPatientId(patientId: String): RetinalPrintPlan? =
            data.values.filter { it.patientId == patientId }.maxByOrNull { it.createdAtMs }

        override fun findRecent(limit: Int): List<RetinalPrintPlan> = data.values.sortedByDescending { it.createdAtMs }.take(limit)
    }

    private class FakeAuditStore : AuditEventStore {
        private val events = mutableListOf<AuditEvent>()

        override fun append(event: AuditEvent) {
            events += event
        }

        override fun recent(limit: Int): List<AuditEvent> = events.takeLast(limit)

        override fun verifyChain(limit: Int): AuditChainVerification {
            return AuditChainVerification(
                valid = true,
                checkedEvents = events.size.coerceAtMost(limit),
                failureIndex = null,
                failureReason = null,
            )
        }
    }
}
