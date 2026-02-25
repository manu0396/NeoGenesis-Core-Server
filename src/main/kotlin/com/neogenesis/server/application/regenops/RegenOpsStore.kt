package com.neogenesis.server.application.regenops

interface RegenOpsStore {
    fun createDraft(
        tenantId: String,
        protocolId: String,
        title: String,
        contentJson: String,
        updatedBy: String,
    ): RegenProtocolDraft

    fun updateDraft(
        tenantId: String,
        protocolId: String,
        title: String,
        contentJson: String,
        updatedBy: String,
    ): RegenProtocolDraft

    fun getDraft(
        tenantId: String,
        protocolId: String,
    ): RegenProtocolDraft?

    fun nextProtocolVersion(
        tenantId: String,
        protocolId: String,
    ): Int

    fun insertProtocolVersion(
        tenantId: String,
        protocolId: String,
        version: Int,
        title: String,
        contentJson: String,
        publishedBy: String,
        changelog: String,
    ): RegenProtocolVersion

    fun getProtocolVersion(
        tenantId: String,
        protocolId: String,
        version: Int,
    ): RegenProtocolVersion?

    fun listProtocols(
        tenantId: String,
        limit: Int,
    ): List<RegenProtocolSummary>

    fun latestProtocolVersion(
        tenantId: String,
        protocolId: String? = null,
    ): RegenProtocolVersion?

    fun requestPublishApproval(
        tenantId: String,
        protocolId: String,
        requestedBy: String,
        reason: String?,
    ): ProtocolPublishApproval

    fun approvePublishApproval(
        tenantId: String,
        approvalId: String,
        approvedBy: String,
        comment: String?,
    ): ProtocolPublishApproval

    fun consumePublishApproval(
        tenantId: String,
        protocolId: String,
        publisherId: String,
    ): ProtocolPublishApproval?

    fun createRun(
        tenantId: String,
        runId: String,
        protocolId: String,
        protocolVersion: Int,
        gatewayId: String?,
        startedBy: String,
    ): RegenRun

    fun getRun(
        tenantId: String,
        runId: String,
    ): RegenRun?

    fun updateRunStatus(
        tenantId: String,
        runId: String,
        status: RegenRunStatus,
        reason: String?,
    ): RegenRun

    fun appendRunEvent(
        tenantId: String,
        runId: String,
        eventType: String,
        source: String,
        payloadJson: String,
        createdAtMs: Long = System.currentTimeMillis(),
    )

    fun listRunEvents(
        tenantId: String,
        runId: String,
        sinceMs: Long,
        sinceSeq: Long,
        limit: Int,
    ): List<RegenRunEvent>

    fun appendTelemetry(points: List<RegenTelemetryPoint>)

    fun listTelemetry(
        tenantId: String,
        runId: String,
        sinceMs: Long,
        sinceSeq: Long,
        limit: Int,
    ): List<RegenTelemetryPoint>

    fun upsertGateway(
        tenantId: String,
        gatewayId: String,
        displayName: String,
        certificateSerial: String,
    ): RegenGateway

    fun heartbeatGateway(
        tenantId: String,
        gatewayId: String,
        certificateSerial: String,
    ): RegenGateway

    fun getGateway(
        tenantId: String,
        gatewayId: String,
    ): RegenGateway?

    fun insertDriftAlert(alert: RegenDriftAlert)

    fun listDriftAlerts(
        tenantId: String,
        runId: String,
        limit: Int,
    ): List<RegenDriftAlert>

    fun countRunEvents(
        tenantId: String,
        runId: String,
    ): Int

    fun countTelemetry(
        tenantId: String,
        runId: String,
    ): Int

    fun appendEvidenceEvent(event: RegenEvidenceEvent)

    fun verifyEvidenceChain(
        tenantId: String,
        limit: Int = 10_000,
    ): RegenEvidenceChainStatus
}
