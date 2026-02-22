package com.neogenesis.server.application.resilience

import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.port.IdempotencyRememberResult
import com.neogenesis.server.application.port.RequestIdempotencyStore
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.security.MessageDigest

class RequestIdempotencyService(
    private val store: RequestIdempotencyStore,
    private val metricsService: OperationalMetricsService,
    private val ttlSeconds: Long
) {
    fun assertOrRemember(
        operation: String,
        idempotencyKey: String,
        canonicalPayload: String
    ) {
        val payloadHash = sha256(canonicalPayload)
        when (
            store.remember(
                operation = operation,
                key = idempotencyKey,
                payloadHash = payloadHash,
                ttlSeconds = ttlSeconds
            )
        ) {
            IdempotencyRememberResult.STORED -> Unit
            IdempotencyRememberResult.DUPLICATE_MATCH -> {
                metricsService.recordIdempotencyDuplicate(operation, "match")
            }
            IdempotencyRememberResult.DUPLICATE_MISMATCH -> {
                metricsService.recordIdempotencyDuplicate(operation, "mismatch")
                throw ConflictException(
                    code = "idempotency_key_payload_mismatch",
                    message = "Idempotency key already used with different payload"
                )
            }
        }
    }

    private fun sha256(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
