package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ControlActionType {
    MAINTAIN,
    ADJUST,
    EMERGENCY_HALT,
}

data class ControlCommand(
    val tenantId: String,
    val commandId: String,
    val printerId: String,
    val actionType: ControlActionType,
    val adjustPressure: Float = 0.0f,
    val adjustSpeed: Float = 0.0f,
    val reason: String,
)
