package com.neogenesis.server.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class OperationalMetricsService(
    private val meterRegistry: MeterRegistry
) {
    fun recordTelemetryIngest(source: String) {
        Counter.builder("neogenesis_telemetry_ingest_total")
            .description("Total telemetry messages ingested")
            .tag("source", source)
            .register(meterRegistry)
            .increment()
    }

    fun recordControlDecision(actionType: String) {
        Counter.builder("neogenesis_control_decisions_total")
            .description("Total control decisions emitted")
            .tag("action", actionType)
            .register(meterRegistry)
            .increment()
    }

    fun recordClinicalDocument(documentType: String) {
        Counter.builder("neogenesis_clinical_ingest_total")
            .description("Total clinical documents ingested")
            .tag("type", documentType)
            .register(meterRegistry)
            .increment()
    }

    fun recordAuthzDenied() {
        Counter.builder("neogenesis_authz_denied_total")
            .description("Total authorization denied events")
            .register(meterRegistry)
            .increment()
    }

    fun recordAuditEvent(action: String, outcome: String) {
        Counter.builder("neogenesis_audit_events_total")
            .description("Total audit events")
            .tag("action", action)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
    }

    fun recordProcessingLatency(durationNanos: Long) {
        Timer.builder("neogenesis_telemetry_processing_latency")
            .description("Telemetry processing latency")
            .publishPercentileHistogram()
            .register(meterRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }

    fun recordLatencyBudgetBreach(source: String) {
        Counter.builder("neogenesis_latency_budget_breaches_total")
            .description("Telemetry processing latency budget breaches")
            .tag("source", source)
            .register(meterRegistry)
            .increment()
    }

    fun recordRetinaPlanCreated() {
        Counter.builder("neogenesis_retina_plans_created_total")
            .description("Total retinal print plans created")
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxEvent(eventType: String) {
        Counter.builder("neogenesis_outbox_events_total")
            .description("Total outbox events produced")
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxProcessed() {
        Counter.builder("neogenesis_outbox_processed_total")
            .description("Total outbox events marked as processed")
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxFailed(eventType: String) {
        Counter.builder("neogenesis_outbox_failed_total")
            .description("Total outbox events marked as failed")
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxRetry(eventType: String) {
        Counter.builder("neogenesis_outbox_retry_total")
            .description("Total outbox events scheduled for retry")
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxDeadLetter(eventType: String) {
        Counter.builder("neogenesis_outbox_dead_letter_total")
            .description("Total outbox events moved to dead letter")
            .tag("event_type", eventType)
            .register(meterRegistry)
            .increment()
    }

    fun recordOutboxReplay() {
        Counter.builder("neogenesis_outbox_replay_total")
            .description("Total dead-letter events replayed")
            .register(meterRegistry)
            .increment()
    }

    fun recordSessionStatusTransition(status: String) {
        Counter.builder("neogenesis_print_session_transitions_total")
            .description("Print session status transitions")
            .tag("status", status)
            .register(meterRegistry)
            .increment()
    }

    fun recordPacsFetch(outcome: String) {
        Counter.builder("neogenesis_pacs_fetch_total")
            .description("Total PACS/DICOMweb fetch operations")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
    }

    fun recordMllpInbound() {
        Counter.builder("neogenesis_hl7_mllp_inbound_total")
            .description("Total inbound HL7 MLLP messages")
            .register(meterRegistry)
            .increment()
    }

    fun recordMllpOutbound(outcome: String) {
        Counter.builder("neogenesis_hl7_mllp_outbound_total")
            .description("Total outbound HL7 MLLP messages")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
    }

    fun recordCircuitBreakerEvent(integration: String, event: String) {
        Counter.builder("neogenesis_integration_circuit_breaker_total")
            .description("Circuit breaker state transitions and events")
            .tag("integration", integration)
            .tag("event", event)
            .register(meterRegistry)
            .increment()
    }

    fun recordIntegrationTimeout(integration: String, operation: String) {
        Counter.builder("neogenesis_integration_timeout_total")
            .description("Integration timeout budget breaches")
            .tag("integration", integration)
            .tag("operation", operation)
            .register(meterRegistry)
            .increment()
    }

    fun recordIdempotencyDuplicate(operation: String, outcome: String) {
        Counter.builder("neogenesis_idempotency_duplicate_total")
            .description("Idempotency duplicate events")
            .tag("operation", operation)
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment()
    }
}
