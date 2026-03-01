package com.neogenesis.server.modules.evidence

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class EvidenceRunReport(
    val schemaVersion: String,
    val jobId: String,
    val tenantId: String,
    val actorId: String,
    val correlationId: String,
    val generatedAt: String,
    val serverVersion: String,
    val telemetryCount: Int,
    val twinCount: Int,
    val auditEventCount: Int,
    val lastTelemetryAt: String?,
    val lastTwinAt: String?,
) {
    fun toCsv(): String {
        val header =
            listOf(
                "schema_version",
                "job_id",
                "tenant_id",
                "actor_id",
                "correlation_id",
                "generated_at",
                "server_version",
                "telemetry_count",
                "twin_count",
                "audit_event_count",
                "last_telemetry_at",
                "last_twin_at",
            )
        val row =
            listOf(
                schemaVersion,
                jobId,
                tenantId,
                actorId,
                correlationId,
                generatedAt,
                serverVersion,
                telemetryCount.toString(),
                twinCount.toString(),
                auditEventCount.toString(),
                lastTelemetryAt.orEmpty(),
                lastTwinAt.orEmpty(),
            )
        return header.joinToString(",") + "\n" + row.joinToString(",") { csvEscape(it) }
    }

    companion object {
        fun from(
            jobId: String,
            tenantId: String,
            actorId: String,
            correlationId: String,
            telemetryRepository: TelemetryRepository,
            twinMetricsRepository: TwinMetricsRepository,
            auditLogRepository: AuditLogRepository,
            serverVersion: String,
        ): EvidenceRunReport {
            val telemetry = telemetryRepository.listByJob(jobId, null, null, 5_000)
            val twin = twinMetricsRepository.listByJob(jobId, null, null, 5_000)
            val audit = auditLogRepository.listByJob(jobId, 10_000)
            return EvidenceRunReport(
                schemaVersion = "1",
                jobId = jobId,
                tenantId = tenantId,
                actorId = actorId,
                correlationId = correlationId,
                generatedAt = Instant.now().toString(),
                serverVersion = serverVersion,
                telemetryCount = telemetry.size,
                twinCount = twin.size,
                auditEventCount = audit.size,
                lastTelemetryAt = telemetry.maxByOrNull { it.recordedAt }?.recordedAt?.toString(),
                lastTwinAt = twin.maxByOrNull { it.recordedAt }?.recordedAt?.toString(),
            )
        }
    }
}

private fun csvEscape(value: String): String {
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
    return value
}
