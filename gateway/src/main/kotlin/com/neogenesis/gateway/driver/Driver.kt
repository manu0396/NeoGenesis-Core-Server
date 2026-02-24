package com.neogenesis.gateway.driver

import kotlinx.coroutines.flow.Flow

interface Driver {
    val name: String

    fun telemetryStream(): Flow<DriverTelemetry>

    fun eventStream(): Flow<DriverRunEvent>

    fun close()
}

data class DriverTelemetry(
    val runId: String,
    val metricKey: String,
    val metricValue: Double,
    val unit: String,
    val driftScore: Double,
    val recordedAtMs: Long,
)

data class DriverRunEvent(
    val runId: String,
    val eventType: String,
    val payloadJson: String,
    val createdAtMs: Long,
)
