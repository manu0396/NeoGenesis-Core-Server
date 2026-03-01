package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class DeadLetterOutboxEvent(
    val id: Long,
    val sourceOutboxId: Long,
    val eventType: String,
    val partitionKey: String,
    val payloadJson: String,
    val attempts: Int,
    val failureReason: String,
    val createdAtMs: Long,
    val failedAtMs: Long,
)
