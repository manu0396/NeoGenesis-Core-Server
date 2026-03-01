package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PrintSessionStatus {
    CREATED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABORTED,
}

@Serializable
data class PrintSession(
    val sessionId: String,
    val printerId: String,
    val planId: String,
    val patientId: String,
    val status: PrintSessionStatus,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
