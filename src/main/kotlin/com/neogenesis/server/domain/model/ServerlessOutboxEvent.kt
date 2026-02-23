package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class OutboxEventStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED,
}

@Serializable
data class ServerlessOutboxEvent(
    val id: Long,
    val eventType: String,
    val partitionKey: String,
    val payloadJson: String,
    val status: OutboxEventStatus,
    val attempts: Int,
    val createdAtMs: Long,
    val processingStartedAtMs: Long?,
    val processedAtMs: Long?,
    val nextAttemptAtMs: Long?,
    val lastError: String?,
)
