package com.neogenesis.server.modules.evidence

import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class EvidenceRunReport(
    val jobId: String,
    val generatedAt: String,
    val serverVersion: String,
    val telemetryCount: Int,
    val twinCount: Int,
    val auditEventCount: Int,
    val lastTelemetryAt: String?,
    val lastTwinAt: String?,
) {
    fun toCsv(): String {
        val lines = mutableListOf<String>()
        lines += "section,key,value"
        lines += "metadata,jobId,$jobId"
        lines += "metadata,generatedAt,$generatedAt"
        lines += "metadata,serverVersion,$serverVersion"
        lines += "counts,telemetry,$telemetryCount"
        lines += "counts,twin,$twinCount"
        lines += "counts,auditEvents,$auditEventCount"
        lines += "timing,lastTelemetryAt,${lastTelemetryAt ?: ""}"
        lines += "timing,lastTwinAt,${lastTwinAt ?: ""}"
        return lines.joinToString("\n")
    }

    companion object {
        fun from(
            jobId: String,
            telemetryRepository: TelemetryRepository,
            twinMetricsRepository: TwinMetricsRepository,
            auditLogRepository: AuditLogRepository,
            serverVersion: String,
        ): EvidenceRunReport {
            val telemetry = telemetryRepository.listByJob(jobId, null, null, 5_000)
            val twin = twinMetricsRepository.listByJob(jobId, null, null, 5_000)
            val audit = auditLogRepository.listByJob(jobId, 10_000)
            return EvidenceRunReport(
                jobId = jobId,
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
