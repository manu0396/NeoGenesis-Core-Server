package com.neogenesis.server.application

import com.neogenesis.server.domain.model.TelemetryState
import java.util.concurrent.ConcurrentHashMap

interface TelemetrySnapshotService {
    fun update(telemetry: TelemetryState)

    fun findByPrinterId(printerId: String): TelemetryState?

    fun findAll(): List<TelemetryState>
}

class InMemoryTelemetrySnapshotService : TelemetrySnapshotService {
    private val latestByPrinter = ConcurrentHashMap<String, TelemetryState>()

    override fun update(telemetry: TelemetryState) {
        latestByPrinter[telemetry.printerId] = telemetry
    }

    override fun findByPrinterId(printerId: String): TelemetryState? {
        return latestByPrinter[printerId]
    }

    override fun findAll(): List<TelemetryState> {
        return latestByPrinter.values
            .sortedByDescending { it.timestampMs }
    }
}
