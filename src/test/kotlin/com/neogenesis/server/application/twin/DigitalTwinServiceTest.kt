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
        val tenantId = "test-tenant"

        val telemetry =
            TelemetryState(
                printerId = "printer-01",
                timestampMs = 1_700_000_000,
                nozzleTempCelsius = 37.1f,
                extrusionPressureKPa = 117.0f,
                cellViabilityIndex = 0.95f,
            )
        val command =
            ControlCommand(
                tenantId = tenantId,
                commandId = "cmd-1",
                printerId = "printer-01",
                actionType = ControlActionType.MAINTAIN,
                reason = "ok",
            )

        val updated = service.updateFromTelemetry(tenantId, telemetry, command)

        assertEquals("printer-01", updated.printerId)
        assertEquals(tenantId, updated.tenantId)
        assertNotNull(service.findByPrinterId(tenantId, "printer-01"))
    }

    private class FakeTwinStore : DigitalTwinStore {
        private val store = mutableMapOf<String, DigitalTwinState>()

        override fun upsert(state: DigitalTwinState) {
            store[state.printerId] = state
        }

        override fun findByPrinterId(
            tenantId: String,
            printerId: String,
        ): DigitalTwinState? = store[printerId]?.takeIf { it.tenantId == tenantId }

        override fun findAll(tenantId: String): List<DigitalTwinState> = store.values.filter { it.tenantId == tenantId }
    }
}
