package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CapaStatus {
    OPEN,
    IN_PROGRESS,
    CLOSED
}

@Serializable
data class CapaRecord(
    val id: Long? = null,
    val title: String,
    val description: String,
    val requirementId: String,
    val owner: String,
    val status: CapaStatus = CapaStatus.OPEN,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
