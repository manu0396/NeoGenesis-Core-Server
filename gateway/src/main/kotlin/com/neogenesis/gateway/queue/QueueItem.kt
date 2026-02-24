package com.neogenesis.gateway.queue

enum class QueueItemType {
    TELEMETRY,
    RUN_EVENT,
}

data class QueueItem(
    val id: String,
    val type: QueueItemType,
    val payload: String,
    val createdAtMs: Long,
)
