package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LatencyBreachEvent(
    val tenantId: String,
    val printerId: String,
    val source: String,
    val durationMs: Double,
    val thresholdMs: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
)
