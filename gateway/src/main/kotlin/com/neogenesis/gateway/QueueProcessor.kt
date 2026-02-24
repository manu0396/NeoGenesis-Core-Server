package com.neogenesis.gateway

import com.neogenesis.gateway.queue.OfflineQueue
import com.neogenesis.gateway.queue.QueueItem
import com.neogenesis.gateway.queue.QueueItemType
import com.neogenesis.grpc.GatewayRunEvent
import com.neogenesis.grpc.GatewayTelemetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class QueueProcessor(
    private val client: GatewayClient,
    private val queue: OfflineQueue,
    private val config: GatewayConfig,
    private val limiter: BackpressureLimiter,
) {
    fun runBlockingLoop() = runBlocking {
        while (true) {
            val batch = queue.peek(maxOf(config.telemetryBatchSize, config.eventsBatchSize))
            if (batch.isEmpty()) {
                delay(500)
                continue
            }

            val telemetry = mutableListOf<GatewayTelemetry>()
            val events = mutableListOf<GatewayRunEvent>()
            batch.forEach { item ->
                when (item.type) {
                    QueueItemType.TELEMETRY -> telemetry += QueueSerializer.deserialize(item) as GatewayTelemetry
                    QueueItemType.RUN_EVENT -> events += QueueSerializer.deserialize(item) as GatewayRunEvent
                }
            }

            if (telemetry.isNotEmpty()) {
                if (limiter.acquire(telemetry.size, 1000)) {
                    val ack = client.pushTelemetry(telemetry)
                    if (ack.accepted > 0) {
                        val ackIds = batch.filter { it.type == QueueItemType.TELEMETRY }.map { it.id }
                        queue.ack(ackIds)
                    }
                }
            }

            if (events.isNotEmpty()) {
                val ack = client.pushRunEvents(events)
                if (ack.accepted > 0) {
                    val ackIds = batch.filter { it.type == QueueItemType.RUN_EVENT }.map { it.id }
                    queue.ack(ackIds)
                }
            }

            delay(200)
        }
    }

    fun enqueueTelemetry(telemetry: GatewayTelemetry) {
        val payload = QueueSerializer.serializeTelemetry(telemetry)
        queue.append(
            QueueItem(
                id = Idempotency.hash(payload),
                type = QueueItemType.TELEMETRY,
                payload = payload,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun enqueueEvent(event: GatewayRunEvent) {
        val payload = QueueSerializer.serializeRunEvent(event)
        queue.append(
            QueueItem(
                id = Idempotency.hash(payload),
                type = QueueItemType.RUN_EVENT,
                payload = payload,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }
}
