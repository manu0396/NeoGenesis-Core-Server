package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RiskRecord(
    val riskId: String,
    val hazardDescription: String,
    val severity: Int,
    val probability: Int,
    val detectability: Int,
    val controls: String,
    val residualRiskLevel: Int,
    val linkedRequirementId: String?,
    val owner: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
