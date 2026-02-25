package com.neogenesis.server.application.regenops

enum class RegenRunStatus {
    RUNNING,
    PAUSED,
    ABORTED,
}

data class RegenProtocolDraft(
    val tenantId: String,
    val protocolId: String,
    val title: String,
    val contentJson: String,
    val updatedBy: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class RegenProtocolVersion(
    val tenantId: String,
    val protocolId: String,
    val version: Int,
    val title: String,
    val contentJson: String,
    val publishedBy: String,
    val changelog: String,
    val createdAtMs: Long,
)

data class RegenProtocolSummary(
    val tenantId: String,
    val protocolId: String,
    val title: String,
    val latestVersion: Int,
    val hasDraft: Boolean,
    val updatedAtMs: Long,
)

data class ProtocolPublishApproval(
    val id: String,
    val tenantId: String,
    val protocolId: String,
    val status: String,
    val requestedBy: String,
    val requestedAtMs: Long,
    val reason: String?,
    val approvedBy: String?,
    val approvedAtMs: Long?,
    val approvalComment: String?,
    val consumedBy: String?,
    val consumedAtMs: Long?,
)

data class RegenRun(
    val tenantId: String,
    val runId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val status: RegenRunStatus,
    val gatewayId: String?,
    val startedBy: String,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val pausedAtMs: Long?,
    val abortedAtMs: Long?,
    val abortReason: String?,
)

data class RegenRunEvent(
    val tenantId: String,
    val runId: String,
    val seq: Long = 0,
    val eventType: String,
    val source: String,
    val payloadJson: String,
    val createdAtMs: Long,
)

data class RegenTelemetryPoint(
    val tenantId: String,
    val runId: String,
    val seq: Long = 0,
    val gatewayId: String,
    val metricKey: String,
    val metricValue: Double,
    val unit: String,
    val driftScore: Double,
    val recordedAtMs: Long,
)

data class RegenGateway(
    val tenantId: String,
    val gatewayId: String,
    val displayName: String,
    val certificateSerial: String,
    val status: String,
    val lastHeartbeatAtMs: Long?,
    val updatedAtMs: Long,
)

data class RegenDriftAlert(
    val tenantId: String,
    val runId: String,
    val severity: String,
    val metricKey: String,
    val metricValue: Double,
    val threshold: Double,
    val createdAtMs: Long,
)

data class RegenEvidenceEvent(
    val tenantId: String,
    val actionType: String,
    val actorId: String,
    val resourceType: String,
    val resourceId: String,
    val payloadJson: String,
    val createdAtMs: Long,
)

data class RegenEvidenceChainStatus(
    val valid: Boolean,
    val checked: Int,
    val failureIndex: Int?,
    val reason: String?,
)

data class RegenReproducibilityScore(
    val tenantId: String,
    val runId: String,
    val score: Double,
    val eventCount: Int,
    val telemetryCount: Int,
    val driftAlertCount: Int,
)

data class RegenRunReport(
    val tenantId: String,
    val runId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val status: RegenRunStatus,
    val reproducibilityScore: Double,
    val eventsJson: String,
    val telemetryJson: String,
    val evidenceChainValid: Boolean,
    val generatedAtMs: Long,
)
