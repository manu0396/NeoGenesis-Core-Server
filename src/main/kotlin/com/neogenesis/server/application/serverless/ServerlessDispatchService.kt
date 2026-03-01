package com.neogenesis.server.application.serverless

import com.neogenesis.server.application.port.OutboxEventStore
import com.neogenesis.server.domain.model.DeadLetterOutboxEvent
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import kotlin.math.min
import kotlin.random.Random

sealed interface PublishResult {
    data object Success : PublishResult
    data class RetryableFailure(val reason: String) : PublishResult
    data class FatalFailure(val reason: String) : PublishResult
}

fun interface OutboxEventPublisher {
    fun publish(event: ServerlessOutboxEvent): PublishResult
}

data class OutboxRetryPolicy(
    val maxRetries: Int,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long
)

class ServerlessDispatchService(
    private val outboxEventStore: OutboxEventStore,
    private val metricsService: OperationalMetricsService,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val retryPolicy: OutboxRetryPolicy
) {

    fun enqueue(eventType: String, partitionKey: String, payloadJson: String) {
        outboxEventStore.enqueue(eventType, partitionKey, payloadJson)
        metricsService.recordOutboxEvent(eventType)
    }

    fun pending(limit: Int = 100): List<ServerlessOutboxEvent> = outboxEventStore.pending(limit)

    fun deadLetter(limit: Int = 100): List<DeadLetterOutboxEvent> = outboxEventStore.deadLetter(limit)

    fun replayDeadLetter(deadLetterId: Long): Boolean {
        val replayed = outboxEventStore.replayDeadLetter(deadLetterId)
        if (replayed) {
            metricsService.recordOutboxReplay()
        }
        return replayed
    }

    fun acknowledge(eventId: Long) {
        outboxEventStore.markProcessed(eventId)
        metricsService.recordOutboxProcessed()
    }

    fun drainPending(limit: Int): Int {
        val events = outboxEventStore.pending(limit)
        if (events.isEmpty()) {
            return 0
        }

        var processed = 0
        events.forEach { event ->
            val result = runCatching { outboxEventPublisher.publish(event) }
                .getOrElse { PublishResult.RetryableFailure(it.message ?: "publish exception") }

            when (result) {
                PublishResult.Success -> {
                    outboxEventStore.markProcessed(event.id)
                    metricsService.recordOutboxProcessed()
                }
                is PublishResult.RetryableFailure -> {
                    val nextAttempt = event.attempts + 1
                    if (nextAttempt >= retryPolicy.maxRetries) {
                        outboxEventStore.moveToDeadLetter(event.id, result.reason)
                        metricsService.recordOutboxFailed(event.eventType)
                        metricsService.recordOutboxDeadLetter(event.eventType)
                    } else {
                        val backoff = exponentialBackoffMs(event.attempts)
                        outboxEventStore.scheduleRetry(
                            eventId = event.id,
                            nextAttemptAtMs = System.currentTimeMillis() + backoff,
                            failureReason = result.reason
                        )
                        metricsService.recordOutboxRetry(event.eventType)
                    }
                }
                is PublishResult.FatalFailure -> {
                    outboxEventStore.moveToDeadLetter(event.id, result.reason)
                    metricsService.recordOutboxFailed(event.eventType)
                    metricsService.recordOutboxDeadLetter(event.eventType)
                }
            }
            processed++
        }
        return processed
    }

    private fun exponentialBackoffMs(currentAttempts: Int): Long {
        val exponent = currentAttempts.coerceIn(0, 30)
        val base = retryPolicy.baseBackoffMs.coerceAtLeast(1L)
        val raw = base * (1L shl exponent)
        val capped = min(raw, retryPolicy.maxBackoffMs.coerceAtLeast(base))
        val jitter = Random.nextLong(0, (base / 3).coerceAtLeast(1L))
        return capped + jitter
    }
}
