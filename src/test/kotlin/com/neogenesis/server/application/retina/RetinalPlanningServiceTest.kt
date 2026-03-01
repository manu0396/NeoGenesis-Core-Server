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
        val tenantId = "test-tenant"

        val plan =
            service.createPlanFromDicom(
                tenantId = tenantId,
                patientId = "patient-1",
                sourceDocumentId = "dicom-1",
                metadata = mapOf("layerCount" to "8", "retinalThicknessMicrons" to "300"),
                actor = "tester",
            )

        assertEquals("patient-1", plan.patientId)
        assertEquals(tenantId, plan.tenantId)
        assertEquals(8, plan.layers.size)
        assertNotNull(store.findByPlanId(tenantId, plan.planId))
    }

    private class FakeRetinalPlanStore : RetinalPlanStore {
        private val data = mutableMapOf<String, RetinalPrintPlan>()

        override fun save(plan: RetinalPrintPlan) {
            data[plan.planId] = plan
        }

        override fun findByPlanId(
            tenantId: String,
            planId: String,
        ): RetinalPrintPlan? = data[planId]?.takeIf { it.tenantId == tenantId }

        override fun findLatestByPatientId(
            tenantId: String,
            patientId: String,
        ): RetinalPrintPlan? = data.values.filter { it.tenantId == tenantId && it.patientId == patientId }.maxByOrNull { it.createdAtMs }

        override fun findRecent(
            tenantId: String,
            limit: Int,
        ): List<RetinalPrintPlan> = data.values.filter { it.tenantId == tenantId }.sortedByDescending { it.createdAtMs }.take(limit)
    }

    private class FakeAuditStore : AuditEventStore {
        private val events = mutableListOf<AuditEvent>()

        override fun append(event: AuditEvent) {
            events += event
        }

        override fun recent(
            tenantId: String,
            limit: Int,
        ): List<AuditEvent> = events.filter { it.tenantId == tenantId }.takeLast(limit)

        override fun verifyChain(
            tenantId: String,
            limit: Int,
        ): AuditChainVerification {
            return AuditChainVerification(
                valid = true,
                checkedEvents = events.filter { it.tenantId == tenantId }.size.coerceAtMost(limit),
                failureIndex = null,
                failureReason = null,
            )
        }
    }
}
