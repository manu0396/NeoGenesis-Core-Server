package com.neogenesis.server.domain.model

data class TelemetryEvent(
    val tenantId: String,
    val telemetry: TelemetryState,
    val source: String,
    val createdAtMs: Long = System.currentTimeMillis(),
)
