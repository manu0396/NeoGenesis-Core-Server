package com.neogenesis.gateway.driver

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class SimulatedDriver(
    private val runId: String,
) : Driver {
    override val name: String = "simulated"

    override fun telemetryStream(): Flow<DriverTelemetry> =
        flow {
            while (true) {
                emit(
                    DriverTelemetry(
                        runId = runId,
                        metricKey = "pressure_kpa",
                        metricValue = 90 + Random.nextDouble(0.0, 5.0),
                        unit = "kPa",
                        driftScore = Random.nextDouble(0.0, 1.0),
                        recordedAtMs = System.currentTimeMillis(),
                    ),
                )
                delay(500)
            }
        }

    override fun eventStream(): Flow<DriverRunEvent> =
        flow {
            while (true) {
                emit(
                    DriverRunEvent(
                        runId = runId,
                        eventType = "sim.tick",
                        payloadJson = "{\"source\":\"sim\"}",
                        createdAtMs = System.currentTimeMillis(),
                    ),
                )
                delay(5_000)
            }
        }

    override fun close() = Unit
}
