package com.neogenesis.gateway.connector

import kotlinx.coroutines.withTimeout
import java.time.Instant

enum class Capability {
    Telemetry,
    Commands,
    Diagnostics,
}

data class TelemetrySchema(
    val name: String,
    val version: String,
    val fields: List<TelemetryField>,
)

data class TelemetryField(
    val name: String,
    val type: String,
    val unit: String? = null,
)

data class DriverContext(
    val tenantId: String,
    val correlationId: String,
    val config: Map<String, String>,
)

data class TelemetryEvent(
    val timestamp: Instant,
    val payload: Map<String, String>,
)

data class DriverHealth(
    val status: String,
    val details: Map<String, String> = emptyMap(),
)

interface Driver {
    val id: String
    val capabilities: Set<Capability>
    val telemetrySchema: TelemetrySchema

    suspend fun init(context: DriverContext)

    suspend fun start()

    suspend fun stop()

    suspend fun health(): DriverHealth

    suspend fun readTelemetry(): TelemetryEvent
}

class SandboxedDriver(
    private val delegate: Driver,
    private val timeoutMs: Long,
) : Driver {
    override val id: String = delegate.id
    override val capabilities: Set<Capability> = delegate.capabilities
    override val telemetrySchema: TelemetrySchema = delegate.telemetrySchema

    override suspend fun init(context: DriverContext) {
        withTimeout(timeoutMs) {
            delegate.init(context)
        }
    }

    override suspend fun start() {
        withTimeout(timeoutMs) {
            delegate.start()
        }
    }

    override suspend fun stop() {
        withTimeout(timeoutMs) {
            delegate.stop()
        }
    }

    override suspend fun health(): DriverHealth =
        withTimeout(timeoutMs) {
            delegate.health()
        }

    override suspend fun readTelemetry(): TelemetryEvent =
        withTimeout(timeoutMs) {
            delegate.readTelemetry()
        }
}
