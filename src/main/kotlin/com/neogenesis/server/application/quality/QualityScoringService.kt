package com.neogenesis.server.application.quality

import com.neogenesis.server.domain.model.TelemetryState
import kotlin.math.abs
import kotlin.math.exp

data class ReproducibilityScore(
    val score: Float,
    val metrics: Map<String, Float>,
    val driftDetected: Boolean,
)

class QualityScoringService {
    fun calculateScore(
        actual: List<TelemetryState>,
        expected: List<TelemetryState>, // From baseline/protocol
    ): ReproducibilityScore {
        if (actual.isEmpty() || expected.isEmpty()) {
            return ReproducibilityScore(0.0f, emptyMap(), true)
        }

        // Align by time/step (simplified for MVP)
        val tempDrift = actual.zip(expected).map { (a, e) -> abs(a.nozzleTempCelsius - e.nozzleTempCelsius) }.average().toFloat()
        val pressureDrift = actual.zip(expected).map { (a, e) -> abs(a.extrusionPressureKPa - e.extrusionPressureKPa) }.average().toFloat()
        val viabilityDrift = actual.zip(expected).map { (a, e) -> abs(a.cellViabilityIndex - e.cellViabilityIndex) }.average().toFloat()

        // Scoring logic: 1.0 is perfect, 0.0 is total drift
        // Using exponential decay for drifts
        val sTemp = exp(-tempDrift / 2.0f)
        val sPressure = exp(-pressureDrift / 15.0f)
        val sViability = exp(-viabilityDrift / 0.1f)

        val totalScore = (sTemp * 0.3f + sPressure * 0.3f + sViability * 0.4f)
        
        val metrics = mapOf(
            "temp_drift" to tempDrift,
            "pressure_drift" to pressureDrift,
            "viability_drift" to viabilityDrift
        )

        return ReproducibilityScore(
            score = totalScore,
            metrics = metrics,
            driftDetected = totalScore < 0.85f
        )
    }
}
