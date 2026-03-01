package com.neogenesis.server.application.telemetry

import com.neogenesis.server.application.ControlDecisionService
import com.neogenesis.server.application.port.PrintSessionStore
import com.neogenesis.server.application.port.RetinalPlanStore
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.PrintSession
import com.neogenesis.server.domain.model.PrintSessionStatus
import com.neogenesis.server.domain.model.RetinalControlConstraints
import com.neogenesis.server.domain.model.RetinalLayerSpec
import com.neogenesis.server.domain.model.RetinalPrintPlan
import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.domain.policy.DefaultTelemetrySafetyPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class ClosedLoopControlServiceTest {
    @Test
    fun `emits emergency halt when morphology exceeds retinal threshold`() {
        val tenantId = "test-tenant"
        val sessionStore =
            FakePrintSessionStore(
                PrintSession(
                    tenantId = tenantId,
                    sessionId = "s1",
                    printerId = "printer-1",
                    planId = "plan-1",
                    patientId = "p1",
                    status = PrintSessionStatus.ACTIVE,
                ),
            )
        val planStore =
            FakeRetinalPlanStore(
                RetinalPrintPlan(
                    tenantId = tenantId,
                    planId = "plan-1",
                    patientId = "p1",
                    sourceDocumentId = "d1",
                    blueprintVersion = "retina-2027.1",
                    layers =
                        listOf(
                            RetinalLayerSpec("Layer", 20f, 1000, 0.7f),
                        ),
                    constraints =
                        RetinalControlConstraints(
                            targetNozzleTempCelsius = 36.8f,
                            tempToleranceCelsius = 0.8f,
                            targetPressureKPa = 110f,
                            pressureToleranceKPa = 8f,
                            minCellViability = 0.9f,
                            maxMorphologicalDefectProbability = 0.12f,
                            maxNirIiTempCelsius = 38.5f,
                            targetBioInkPh = 7.35f,
                            phTolerance = 0.12f,
                        ),
                ),
            )

        val service =
            ClosedLoopControlService(
                decisionService = ControlDecisionService(DefaultTelemetrySafetyPolicy()),
                printSessionStore = sessionStore,
                retinalPlanStore = planStore,
            )

        val command =
            service.decide(
                tenantId = tenantId,
                telemetry =
                    TelemetryState(
                        printerId = "printer-1",
                        timestampMs = 1,
                        nozzleTempCelsius = 36.8f,
                        extrusionPressureKPa = 110f,
                        cellViabilityIndex = 0.95f,
                        bioInkPh = 7.35f,
                        nirIiTempCelsius = 37.2f,
                        morphologicalDefectProbability = 0.2f,
                    ),
            )

        assertEquals(ControlActionType.EMERGENCY_HALT, command.actionType)
    }

    private class FakePrintSessionStore(
        private val active: PrintSession?,
    ) : PrintSessionStore {
        override fun create(session: PrintSession) = Unit

        override fun updateStatus(
            tenantId: String,
            sessionId: String,
            status: PrintSessionStatus,
            updatedAtMs: Long,
        ) = Unit

        override fun findBySessionId(
            tenantId: String,
            sessionId: String,
        ): PrintSession? = active?.takeIf { it.tenantId == tenantId && it.sessionId == sessionId }

        override fun findActiveByPrinterId(
            tenantId: String,
            printerId: String,
        ): PrintSession? = active?.takeIf { it.tenantId == tenantId && it.printerId == printerId }

        override fun findActive(
            tenantId: String,
            limit: Int,
        ): List<PrintSession> = listOfNotNull(active).filter { it.tenantId == tenantId }
    }

    private class FakeRetinalPlanStore(
        private val plan: RetinalPrintPlan,
    ) : RetinalPlanStore {
        override fun save(plan: RetinalPrintPlan) = Unit

        override fun findByPlanId(
            tenantId: String,
            planId: String,
        ): RetinalPrintPlan? = plan.takeIf { it.tenantId == tenantId && it.planId == planId }

        override fun findLatestByPatientId(
            tenantId: String,
            patientId: String,
        ): RetinalPrintPlan? = plan.takeIf { it.tenantId == tenantId && it.patientId == patientId }

        override fun findRecent(
            tenantId: String,
            limit: Int,
        ): List<RetinalPrintPlan> = listOf(plan).filter { it.tenantId == tenantId }
    }
}
