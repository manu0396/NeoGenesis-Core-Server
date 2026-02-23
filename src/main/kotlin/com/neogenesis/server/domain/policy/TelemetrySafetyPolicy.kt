package com.neogenesis.server.domain.policy

import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.TelemetryState
import java.util.UUID

interface TelemetrySafetyPolicy {
    fun decide(telemetry: TelemetryState): ControlCommand
}

class DefaultTelemetrySafetyPolicy : TelemetrySafetyPolicy {
    override fun decide(telemetry: TelemetryState): ControlCommand {
        if (
            telemetry.isCriticalViability() ||
            telemetry.isCriticalTemperature() ||
            telemetry.isCriticalPressure() ||
            telemetry.morphologicalDefectProbability > 0.30f ||
            telemetry.bioInkPh < 6.8f ||
            telemetry.bioInkPh > 7.8f
        ) {
            return ControlCommand(
                commandId = commandId(),
                printerId = telemetry.printerId,
                actionType = ControlActionType.EMERGENCY_HALT,
                reason = "Critical biofabrication safety threshold reached",
            )
        }

        val pressureAdjust =
            when {
                telemetry.extrusionPressureKPa < 90.0f -> 6.0f
                telemetry.extrusionPressureKPa > 140.0f -> -6.0f
                else -> 0.0f
            }

        val speedAdjust =
            when {
                telemetry.nozzleTempCelsius < 34.0f -> -0.08f
                telemetry.nozzleTempCelsius > 39.0f -> 0.08f
                else -> 0.0f
            }

        if (pressureAdjust != 0.0f || speedAdjust != 0.0f) {
            return ControlCommand(
                commandId = commandId(),
                printerId = telemetry.printerId,
                actionType = ControlActionType.ADJUST,
                adjustPressure = pressureAdjust,
                adjustSpeed = speedAdjust,
                reason = "Applying closed-loop correction",
            )
        }

        return ControlCommand(
            commandId = commandId(),
            printerId = telemetry.printerId,
            actionType = ControlActionType.MAINTAIN,
            reason = "Telemetry is within expected range",
        )
    }

    private fun commandId(): String = "cmd-${UUID.randomUUID()}"
}
