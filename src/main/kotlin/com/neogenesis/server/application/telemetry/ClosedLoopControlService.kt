package com.neogenesis.server.application.telemetry

import com.neogenesis.server.application.ControlDecisionService
import com.neogenesis.server.application.port.PrintSessionStore
import com.neogenesis.server.application.port.RetinalPlanStore
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.TelemetryState
import java.util.UUID
import kotlin.math.abs

class ClosedLoopControlService(
    private val decisionService: ControlDecisionService,
    private val printSessionStore: PrintSessionStore,
    private val retinalPlanStore: RetinalPlanStore,
) {
    fun decide(tenantId: String, telemetry: TelemetryState): ControlCommand {
        val baseline = decisionService.evaluate(tenantId, telemetry)

        val activeSession =
            printSessionStore.findActiveByPrinterId(tenantId, telemetry.printerId)
                ?: return baseline

        val plan =
            retinalPlanStore.findByPlanId(tenantId, activeSession.planId)
                ?: return baseline.copy(reason = "${baseline.reason}; active session without retinal plan")

        val constraints = plan.constraints

        if (
            telemetry.cellViabilityIndex < constraints.minCellViability ||
            telemetry.morphologicalDefectProbability > constraints.maxMorphologicalDefectProbability ||
            telemetry.nirIiTempCelsius > constraints.maxNirIiTempCelsius ||
            abs(telemetry.bioInkPh - constraints.targetBioInkPh) > constraints.phTolerance
        ) {
            return ControlCommand(
                tenantId = tenantId,
                commandId = commandId(),
                printerId = telemetry.printerId,
                actionType = ControlActionType.EMERGENCY_HALT,
                reason = "Retina plan threshold breach for session ${activeSession.sessionId}",
            )
        }

        val pressureDelta = constraints.targetPressureKPa - telemetry.extrusionPressureKPa
        val pressureAdjust =
            if (abs(pressureDelta) > constraints.pressureToleranceKPa) {
                pressureDelta.coerceIn(-12.0f, 12.0f)
            } else {
                0.0f
            }

        val tempDelta = constraints.targetNozzleTempCelsius - telemetry.nozzleTempCelsius
        val speedAdjust =
            if (abs(tempDelta) > constraints.tempToleranceCelsius) {
                if (tempDelta > 0) -0.10f else 0.10f
            } else {
                0.0f
            }

        if (pressureAdjust != 0.0f || speedAdjust != 0.0f) {
            return ControlCommand(
                tenantId = tenantId,
                commandId = commandId(),
                printerId = telemetry.printerId,
                actionType = ControlActionType.ADJUST,
                adjustPressure = pressureAdjust,
                adjustSpeed = speedAdjust,
                reason = "Retina closed-loop correction for plan ${plan.planId}",
            )
        }

        return baseline.copy(
            reason = "${baseline.reason}; retina constraints satisfied (${plan.planId})",
        )
    }

    private fun commandId(): String = "cmd-${UUID.randomUUID()}"
}
