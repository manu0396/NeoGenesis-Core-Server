package com.neogenesis.server.application.serverless

import com.neogenesis.server.application.port.OutboxEventStore
import com.neogenesis.server.domain.model.DeadLetterOutboxEvent
import com.neogenesis.server.domain.model.OutboxEventStatus
import com.neogenesis.server.domain.model.ServerlessOutboxEvent
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerlessDispatchServiceTest {

    @Test
    fun `marks processed when publisher succeeds`() {
        val store = FakeOutboxEventStore(
            pending = listOf(
                event(id = 1, attempts = 0)
            )
        )
        val service = ServerlessDispatchService(
            outboxEventStore = store,
            metricsService = OperationalMetricsService(SimpleMeterRegistry()),
            outboxEventPublisher = OutboxEventPublisher { PublishResult.Success },
            retryPolicy = OutboxRetryPolicy(maxRetries = 5, baseBackoffMs = 10, maxBackoffMs = 1000)
        )

        service.drainPending(10)

        assertEquals(listOf(1L), store.processed)
        assertEquals(emptyList(), store.retried)
        assertEquals(emptyList(), store.deadLettered)
    }

    @Test
    fun `schedules retry when publisher fails and retries remain`() {
        val store = FakeOutboxEventStore(
            pending = listOf(
                event(id = 2, attempts = 1)
            )
        )
        val service = ServerlessDispatchService(
            outboxEventStore = store,
            metricsService = OperationalMetricsService(SimpleMeterRegistry()),
            outboxEventPublisher = OutboxEventPublisher { PublishResult.RetryableFailure("temporary") },
            retryPolicy = OutboxRetryPolicy(maxRetries = 5, baseBackoffMs = 10, maxBackoffMs = 1000)
        )

        service.drainPending(10)

        assertEquals(emptyList(), store.processed)
        assertEquals(1, store.retried.size)
        assertEquals(emptyList(), store.deadLettered)
    }

    @Test
    fun `moves to dead letter when retries exhausted`() {
        val store = FakeOutboxEventStore(
            pending = listOf(
                event(id = 3, attempts = 4)
            )
        )
        val service = ServerlessDispatchService(
            outboxEventStore = store,
            metricsService = OperationalMetricsService(SimpleMeterRegistry()),
            outboxEventPublisher = OutboxEventPublisher { PublishResult.RetryableFailure("still failing") },
            retryPolicy = OutboxRetryPolicy(maxRetries = 5, baseBackoffMs = 10, maxBackoffMs = 1000)
        )

        service.drainPending(10)

        assertEquals(emptyList(), store.processed)
        assertEquals(emptyList(), store.retried)
        assertEquals(listOf(3L), store.deadLettered.map { it.first })
    }

    private fun event(id: Long, attempts: Int): ServerlessOutboxEvent {
        return ServerlessOutboxEvent(
            id = id,
            eventType = "DICOM_INGESTED",
            partitionKey = "patient-1",
            payloadJson = """{"id":"$id"}""",
            status = OutboxEventStatus.PENDING,
            attempts = attempts,
            createdAtMs = System.currentTimeMillis(),
            processedAtMs = null,
            nextAttemptAtMs = System.currentTimeMillis(),
            lastError = null
        )
    }

    private class FakeOutboxEventStore(
        private val pending: List<ServerlessOutboxEvent>
    ) : OutboxEventStore {
        val processed = mutableListOf<Long>()
        val retried = mutableListOf<Triple<Long, Long, String>>()
        val deadLettered = mutableListOf<Pair<Long, String>>()

        override fun enqueue(eventType: String, partitionKey: String, payloadJson: String) = Unit

        override fun pending(limit: Int): List<ServerlessOutboxEvent> = pending.take(limit)

        override fun markProcessed(eventId: Long) {
            processed += eventId
        }

        override fun scheduleRetry(eventId: Long, nextAttemptAtMs: Long, failureReason: String) {
            retried += Triple(eventId, nextAttemptAtMs, failureReason)
        }

        override fun moveToDeadLetter(eventId: Long, failureReason: String) {
            deadLettered += eventId to failureReason
        }

        override fun deadLetter(limit: Int): List<DeadLetterOutboxEvent> = emptyList()

        override fun replayDeadLetter(deadLetterId: Long): Boolean = true
    }
}
