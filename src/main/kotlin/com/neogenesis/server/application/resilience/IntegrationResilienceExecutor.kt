package com.neogenesis.server.application.resilience

import com.neogenesis.server.application.error.DependencyUnavailableException
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class IntegrationResilienceExecutor(
    private val enabled: Boolean,
    private val timeoutMs: Long,
    private val failureThreshold: Int,
    private val openStateMs: Long,
    private val metricsService: OperationalMetricsService
) {
    private val states = ConcurrentHashMap<String, CircuitState>()
    private val timeoutExecutor = Executors.newCachedThreadPool()

    fun <T> execute(integration: String, operation: String, block: () -> T): T {
        if (!enabled) {
            return block()
        }
        val state = states.computeIfAbsent(integration) { CircuitState() }
        val now = System.currentTimeMillis()
        val openedAt = state.openedUntilMs
        if (openedAt > now) {
            metricsService.recordCircuitBreakerEvent(integration, "open_reject")
            throw DependencyUnavailableException(
                code = "integration_circuit_open",
                message = "Circuit breaker open for integration=$integration"
            )
        }

        return try {
            val future = timeoutExecutor.submit<T> { block() }
            val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            state.failures.set(0)
            state.openedUntilMs = 0L
            metricsService.recordCircuitBreakerEvent(integration, "success")
            result
        } catch (_: TimeoutException) {
            markFailure(state, integration)
            metricsService.recordIntegrationTimeout(integration, operation)
            throw DependencyUnavailableException(
                code = "integration_timeout",
                message = "Timeout budget exceeded for $integration.$operation"
            )
        } catch (error: Exception) {
            markFailure(state, integration)
            metricsService.recordCircuitBreakerEvent(integration, "failure")
            throw error
        }
    }

    fun close() {
        timeoutExecutor.shutdownNow()
    }

    private fun markFailure(state: CircuitState, integration: String) {
        val failures = state.failures.incrementAndGet()
        if (failures >= failureThreshold) {
            state.openedUntilMs = System.currentTimeMillis() + openStateMs
            state.failures.set(0)
            metricsService.recordCircuitBreakerEvent(integration, "open")
        }
    }

    private class CircuitState(
        val failures: AtomicInteger = AtomicInteger(0),
        @Volatile var openedUntilMs: Long = 0L
    )
}
