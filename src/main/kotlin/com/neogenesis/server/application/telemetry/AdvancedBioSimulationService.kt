package com.neogenesis.server.application.telemetry

import com.neogenesis.server.domain.model.TelemetryState
import kotlin.math.exp
import kotlin.math.pow

data class BioSimulationSnapshot(
    val shearStressKPa: Float,
    val predictedViability: Float,
)

/**
 * Bio-physical model integrating:
 * - Ostwald-de Waele power law: tau = K * (gamma_dot^n)
 * - Arrhenius-like viability decay: V(t) = V0 * exp(-k * integral(tau dt))
 */
class AdvancedBioSimulationService(
    private val consistencyIndexK: Float = 0.42f,
    private val flowIndexN: Float = 0.68f,
    private val sensitivityK: Float = 0.004f,
    private val residenceTimeMs: Float = 220.0f,
) {
    fun simulate(telemetry: TelemetryState): BioSimulationSnapshot {
        val tau = calculateShearStress(telemetry)
        val viability =
            predictViabilityLoss(
                currentViability = telemetry.cellViabilityIndex.coerceIn(0.0f, 1.0f),
                shearStressKPa = tau,
                residenceTimeMs = residenceTimeMs,
            )
        return BioSimulationSnapshot(
            shearStressKPa = tau,
            predictedViability = viability,
        )
    }

    fun calculateShearStress(telemetry: TelemetryState): Float {
        val safeK = consistencyIndexK.coerceAtLeast(0.01f)
        val safeN = flowIndexN.coerceIn(0.05f, 1.5f)
        val shearRate = (telemetry.extrusionPressureKPa.coerceAtLeast(0.1f) / safeK).pow(1f / safeN)
        return (safeK * shearRate.pow(safeN)).coerceIn(0.0f, 2_000.0f)
    }

    fun predictViabilityLoss(
        currentViability: Float,
        shearStressKPa: Float,
        residenceTimeMs: Float,
    ): Float {
        if (shearStressKPa < 1.0f) {
            return currentViability
        }
        val survivalRatio = exp(-sensitivityK * shearStressKPa * (residenceTimeMs.coerceAtLeast(1f) / 1_000f))
        return (currentViability * survivalRatio).coerceIn(0.0f, 1.0f)
    }
}
