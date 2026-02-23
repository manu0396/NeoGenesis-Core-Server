package com.neogenesis.server.application.resilience

import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.port.IdempotencyRememberResult
import com.neogenesis.server.application.port.RequestIdempotencyStore
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RequestIdempotencyServiceTest {
    @Test
    fun `throws conflict when key is reused with different payload`() {
        val service =
            RequestIdempotencyService(
                store = InMemoryRequestIdempotencyStore(),
                metricsService = OperationalMetricsService(SimpleMeterRegistry()),
                ttlSeconds = 3600,
            )

        service.assertOrRemember(
            operation = "clinical.fhir.ingest",
            idempotencyKey = "idem-1",
            canonicalPayload = """{"id":"1"}""",
        )

        assertFailsWith<ConflictException> {
            service.assertOrRemember(
                operation = "clinical.fhir.ingest",
                idempotencyKey = "idem-1",
                canonicalPayload = """{"id":"2"}""",
            )
        }
    }

    private class InMemoryRequestIdempotencyStore : RequestIdempotencyStore {
        private val values = mutableMapOf<Pair<String, String>, String>()

        override fun remember(
            operation: String,
            key: String,
            payloadHash: String,
            ttlSeconds: Long,
        ): IdempotencyRememberResult {
            val composite = operation to key
            val existing = values[composite]
            return when {
                existing == null -> {
                    values[composite] = payloadHash
                    IdempotencyRememberResult.STORED
                }
                existing == payloadHash -> IdempotencyRememberResult.DUPLICATE_MATCH
                else -> IdempotencyRememberResult.DUPLICATE_MISMATCH
            }
        }
    }
}
