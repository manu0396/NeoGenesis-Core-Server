package com.neogenesis.server.application.telemetry

import com.neogenesis.server.domain.model.TelemetryState
import kotlin.test.Test
import kotlin.test.assertTrue

class AdvancedBioSimulationServiceTest {
    @Test
    fun `predicts viability degradation under high shear`() {
        val service =
            AdvancedBioSimulationService(
                consistencyIndexK = 0.4f,
                flowIndexN = 0.7f,
                sensitivityK = 0.006f,
                residenceTimeMs = 350f,
            )
        val telemetry =
            TelemetryState(
                printerId = "p-1",
                timestampMs = System.currentTimeMillis(),
                nozzleTempCelsius = 37.0f,
                extrusionPressureKPa = 150f,
                cellViabilityIndex = 0.98f,
            )

        val snapshot = service.simulate(telemetry)

        assertTrue(snapshot.shearStressKPa > 0f)
        assertTrue(snapshot.predictedViability <= telemetry.cellViabilityIndex)
    }
}
