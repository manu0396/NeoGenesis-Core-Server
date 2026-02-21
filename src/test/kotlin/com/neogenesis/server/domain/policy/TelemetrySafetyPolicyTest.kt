package com.neogenesis.server.domain.policy

import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.TelemetryState
import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetrySafetyPolicyTest {

    private val policy = DefaultTelemetrySafetyPolicy()

    @Test
    fun `returns emergency halt for critical viability`() {
        val telemetry = TelemetryState(
            printerId = "printer-a",
            timestampMs = 1,
            nozzleTempCelsius = 36.5f,
            extrusionPressureKPa = 120.0f,
            cellViabilityIndex = 0.80f
        )

        val command = policy.decide(telemetry)

        assertEquals(ControlActionType.EMERGENCY_HALT, command.actionType)
    }

    @Test
    fun `returns adjust for pressure above target range`() {
        val telemetry = TelemetryState(
            printerId = "printer-a",
            timestampMs = 1,
            nozzleTempCelsius = 36.5f,
            extrusionPressureKPa = 150.0f,
            cellViabilityIndex = 0.95f
        )

        val command = policy.decide(telemetry)

        assertEquals(ControlActionType.ADJUST, command.actionType)
    }

    @Test
    fun `returns maintain when telemetry is stable`() {
        val telemetry = TelemetryState(
            printerId = "printer-a",
            timestampMs = 1,
            nozzleTempCelsius = 36.5f,
            extrusionPressureKPa = 115.0f,
            cellViabilityIndex = 0.95f
        )

        val command = policy.decide(telemetry)

        assertEquals(ControlActionType.MAINTAIN, command.actionType)
    }
}
