package com.neogenesis.server.application.twin

import com.neogenesis.server.application.port.DigitalTwinStore
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.DigitalTwinState
import com.neogenesis.server.domain.model.TelemetryState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DigitalTwinServiceTest {

    @Test
    fun `updates digital twin snapshot`() {
        val service = DigitalTwinService(FakeTwinStore())

        val telemetry = TelemetryState(
            printerId = "printer-01",
            timestampMs = 1_700_000_000,
            nozzleTempCelsius = 37.1f,
            extrusionPressureKPa = 117.0f,
            cellViabilityIndex = 0.95f
        )
        val command = ControlCommand(
            commandId = "cmd-1",
            printerId = "printer-01",
            actionType = ControlActionType.MAINTAIN,
            reason = "ok"
        )

        val updated = service.updateFromTelemetry(telemetry, command)

        assertEquals("printer-01", updated.printerId)
        assertNotNull(service.findByPrinterId("printer-01"))
    }

    private class FakeTwinStore : DigitalTwinStore {
        private val store = mutableMapOf<String, DigitalTwinState>()

        override fun upsert(state: DigitalTwinState) {
            store[state.printerId] = state
        }

        override fun findByPrinterId(printerId: String): DigitalTwinState? = store[printerId]

        override fun findAll(): List<DigitalTwinState> = store.values.toList()
    }
}
