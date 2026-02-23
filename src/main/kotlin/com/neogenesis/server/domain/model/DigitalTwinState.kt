package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DigitalTwinState(
    val printerId: String,
    val updatedAtMs: Long,
    val currentViability: Float,
    val predictedViability5m: Float,
    val collapseRiskScore: Float,
    val recommendedAction: ControlActionType,
    val confidence: Float,
)
