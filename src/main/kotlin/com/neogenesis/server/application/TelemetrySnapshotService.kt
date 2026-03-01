package com.neogenesis.server.application

import com.neogenesis.server.domain.model.TelemetryState
import java.util.concurrent.ConcurrentHashMap

interface TelemetrySnapshotService {
    fun update(
        tenantId: String,
        telemetry: TelemetryState,
    )

    fun findByPrinterId(
        tenantId: String,
        printerId: String,
    ): TelemetryState?

    fun findAll(tenantId: String): List<TelemetryState>
}

class InMemoryTelemetrySnapshotService : TelemetrySnapshotService {
    private val snapshots = ConcurrentHashMap<String, Map<String, TelemetryState>>()

    override fun update(
        tenantId: String,
        telemetry: TelemetryState,
    ) {
        snapshots.compute(tenantId) { _, existing ->
            val mutable = (existing ?: emptyMap()).toMutableMap()
            mutable[telemetry.printerId] = telemetry
            mutable
        }
    }

    override fun findByPrinterId(
        tenantId: String,
        printerId: String,
    ): TelemetryState? {
        return snapshots[tenantId]?.get(printerId)
    }

    override fun findAll(tenantId: String): List<TelemetryState> {
        return snapshots[tenantId]?.values?.sortedByDescending { it.timestampMs } ?: emptyList()
    }
}
