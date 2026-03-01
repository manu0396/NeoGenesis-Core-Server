package com.neogenesis.gateway.connector

import java.time.Instant
import kotlin.random.Random

class ExampleConnectorDriver : Driver {
    override val id: String = "example-driver"
    override val capabilities: Set<Capability> = setOf(Capability.Telemetry, Capability.Diagnostics)
    override val telemetrySchema: TelemetrySchema =
        TelemetrySchema(
            name = "example",
            version = "1.0",
            fields = listOf(TelemetryField("temperature_c", "double", "c")),
        )
    private val random = Random(7)
    private var started = false

    override suspend fun init(context: DriverContext) {
        started = false
    }

    override suspend fun start() {
        started = true
    }

    override suspend fun stop() {
        started = false
    }

    override suspend fun health(): DriverHealth {
        val status = if (started) "ok" else "stopped"
        return DriverHealth(status, mapOf("driver" to id))
    }

    override suspend fun readTelemetry(): TelemetryEvent {
        if (!started) error("driver_not_started")
        val value = 36.0 + random.nextDouble(-0.5, 0.5)
        return TelemetryEvent(
            timestamp = Instant.now(),
            payload = mapOf("temperature_c" to "%.2f".format(value)),
        )
    }
}
