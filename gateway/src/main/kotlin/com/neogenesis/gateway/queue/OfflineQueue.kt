package com.neogenesis.gateway.queue

interface OfflineQueue {
    fun append(item: QueueItem)

    fun appendAll(items: List<QueueItem>)

    fun peek(limit: Int): List<QueueItem>

    fun ack(ids: List<String>)

    fun size(): Int
}
