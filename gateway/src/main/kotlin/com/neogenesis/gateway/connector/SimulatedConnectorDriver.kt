package com.neogenesis.gateway.connector

import java.time.Instant
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

class SimulatedConnectorDriver(
    private val seed: Int = 42,
) : Driver {
    override val id: String = "simulated-connector"
    override val capabilities: Set<Capability> = setOf(Capability.Telemetry, Capability.Diagnostics)
    override val telemetrySchema: TelemetrySchema =
        TelemetrySchema(
            name = "simulated",
            version = "1.0",
            fields =
                listOf(
                    TelemetryField("temperature_c", "double", "C"),
                    TelemetryField("pressure_kpa", "double", "kPa"),
                ),
        )
    override val manifest: ConnectorManifest =
        ConnectorManifest(
            id = id,
            name = "Simulated Connector",
            version = "1.0.0",
            vendor = "NeoGenesis",
            capabilities = capabilities,
            telemetrySchema = telemetrySchema,
            description = "Synthetic connector for local validation and certification harness runs.",
            configSchema =
                listOf(
                    ConnectorConfigField(
                        key = "profile",
                        description = "Simulation profile name.",
                        required = false,
                        defaultValue = "default",
                    ),
                ),
        )

    private val random = Random(seed)
    private var started = false
    private var profile = "default"

    override suspend fun init(context: DriverContext) {
        started = false
        profile = context.config["profile"]?.trim().orEmpty().ifBlank { "default" }
    }

    override suspend fun start() {
        started = true
    }

    override suspend fun stop() {
        started = false
    }

    override suspend fun health(): DriverHealth {
        val status = if (started) "ok" else "stopped"
        return DriverHealth(status, mapOf("profile" to profile))
    }

    override suspend fun readTelemetry(): TelemetryEvent {
        if (!started) {
            error("driver_not_started")
        }
        val temperature = 36.5 + random.nextDouble(-0.4, 0.6)
        val pressure = 90.0 + random.nextDouble(-1.0, 1.5)
        return TelemetryEvent(
            timestamp = Instant.now(),
            payload =
                mapOf(
                    "temperature_c" to temperature.roundTo(2),
                    "pressure_kpa" to pressure.roundTo(2),
                    "profile" to profile,
                ),
        )
    }
}

private fun Double.roundTo(decimals: Int): String {
    val factor = 10.0.pow(decimals.toDouble())
    return (round(this * factor) / factor).toString()
}
