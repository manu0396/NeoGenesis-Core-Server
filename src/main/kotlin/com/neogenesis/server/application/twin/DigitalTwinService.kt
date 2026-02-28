package com.neogenesis.server.application.twin

import com.neogenesis.server.application.port.DigitalTwinStore
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.DigitalTwinState
import com.neogenesis.server.domain.model.TelemetryState
import kotlin.math.abs

class DigitalTwinService(
    private val digitalTwinStore: DigitalTwinStore,
) {
    fun updateFromTelemetry(
        tenantId: String,
        telemetry: TelemetryState,
        command: ControlCommand,
        simulatedViability: Float? = null,
        simulatedShearStressKPa: Float? = null,
    ): DigitalTwinState {
        val viability = telemetry.cellViabilityIndex.coerceIn(0.0f, 1.0f)
        val physicsViability = simulatedViability?.coerceIn(0.0f, 1.0f) ?: viability

        val temperaturePenalty =
            (abs(telemetry.nozzleTempCelsius - 37.0f) / 20.0f).coerceIn(0.0f, 1.0f)
        val pressurePenalty =
            (abs(telemetry.extrusionPressureKPa - 110.0f) / 180.0f).coerceIn(0.0f, 1.0f)
        val morphologyPenalty = telemetry.morphologicalDefectProbability.coerceIn(0.0f, 1.0f)
        val phPenalty =
            (abs(telemetry.bioInkPh - 7.35f) / 1.2f).coerceIn(0.0f, 1.0f)
        val nirPenalty =
            (
                (telemetry.nirIiTempCelsius - 37.0f).coerceAtLeast(0.0f) /
                    5.0f
            ).coerceIn(0.0f, 1.0f)
        val viscosityPenalty =
            (abs(telemetry.bioInkViscosityIndex - 0.8f) / 0.8f).coerceIn(0.0f, 1.0f)
        val shearPenalty =
            ((simulatedShearStressKPa ?: 0.0f) / 450.0f).coerceIn(0.0f, 1.0f)
        val emergencyPenalty =
            if (command.actionType == ControlActionType.EMERGENCY_HALT) {
                0.25f
            } else {
                0.0f
            }

        val collapseRisk =
            (
                0.34f * (1.0f - viability) +
                    0.10f * (1.0f - physicsViability) +
                    0.12f * temperaturePenalty +
                    0.08f * pressurePenalty +
                    0.15f * morphologyPenalty +
                    0.08f * phPenalty +
                    0.08f * nirPenalty +
                    0.05f * viscosityPenalty +
                    0.07f * shearPenalty +
                    emergencyPenalty
            ).coerceIn(0.0f, 1.0f)

        val predictedViability =
            (
                (physicsViability + viability) /
                    2.0f -
                    (collapseRisk * 0.16f)
            ).coerceIn(0.0f, 1.0f)
        val confidence =
            (
                1.0f -
                    (
                        (
                            temperaturePenalty +
                                pressurePenalty +
                                morphologyPenalty +
                                phPenalty +
                                nirPenalty +
                                viscosityPenalty +
                                shearPenalty
                        ) / 7.0f
                    )
            ).coerceIn(0.2f, 0.99f)

        val state =
            DigitalTwinState(
                tenantId = tenantId,
                printerId = telemetry.printerId,
                updatedAtMs = telemetry.timestampMs,
                currentViability = viability,
                predictedViability5m = predictedViability,
                collapseRiskScore = collapseRisk,
                recommendedAction = command.actionType,
                confidence = confidence,
            )

        digitalTwinStore.upsert(state)
        return state
    }

    fun findByPrinterId(tenantId: String, printerId: String): DigitalTwinState? = digitalTwinStore.findByPrinterId(tenantId, printerId)

    fun findAll(tenantId: String): List<DigitalTwinState> = digitalTwinStore.findAll(tenantId).sortedByDescending { it.updatedAtMs }
}
