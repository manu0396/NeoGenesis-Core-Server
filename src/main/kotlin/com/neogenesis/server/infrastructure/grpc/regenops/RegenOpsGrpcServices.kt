@file:Suppress("ktlint:standard:import-ordering")

package com.neogenesis.server.infrastructure.grpc.regenops

import com.neogenesis.grpc.CreateDraftRequest
import com.neogenesis.grpc.DiffVersionsRequest
import com.neogenesis.grpc.DiffVersionsResponse
import com.neogenesis.grpc.ExportRunReportRequest
import com.neogenesis.grpc.FetchConfigRequest
import com.neogenesis.grpc.GatewayAck
import com.neogenesis.grpc.GatewayConfig
import com.neogenesis.grpc.GatewayRecord
import com.neogenesis.grpc.GetProtocolVersionRequest
import com.neogenesis.grpc.GetReproducibilityScoreRequest
import com.neogenesis.grpc.GetRunRequest
import com.neogenesis.grpc.HeartbeatRequest
import com.neogenesis.grpc.ListDriftAlertsRequest
import com.neogenesis.grpc.ListDriftAlertsResponse
import com.neogenesis.grpc.ListProtocolsRequest
import com.neogenesis.grpc.ListProtocolsResponse
import com.neogenesis.grpc.MetricsServiceGrpcKt
import com.neogenesis.grpc.ProtocolDraftRecord
import com.neogenesis.grpc.ProtocolServiceGrpcKt
import com.neogenesis.grpc.ProtocolSummary
import com.neogenesis.grpc.ProtocolVersionRecord
import com.neogenesis.grpc.PublishVersionRequest
import com.neogenesis.grpc.PushRunEventsRequest
import com.neogenesis.grpc.PushTelemetryRequest
import com.neogenesis.grpc.RegisterGatewayRequest
import com.neogenesis.grpc.ReproducibilityScoreResponse
import com.neogenesis.grpc.RunControlRequest
import com.neogenesis.grpc.RunEventRecord
import com.neogenesis.grpc.RunRecord
import com.neogenesis.grpc.RunReportResponse
import com.neogenesis.grpc.RunServiceGrpcKt
import com.neogenesis.grpc.StartRunRequest
import com.neogenesis.grpc.StreamRunEventsRequest
import com.neogenesis.grpc.StreamTelemetryRequest
import com.neogenesis.grpc.TelemetryRecord
import com.neogenesis.grpc.UpdateDraftRequest
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenRunEvent
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import com.neogenesis.server.infrastructure.grpc.GrpcPrincipal
import com.neogenesis.server.infrastructure.grpc.requireGrpcGrant
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RegenProtocolGrpcService(
    private val service: RegenOpsService,
) : ProtocolServiceGrpcKt.ProtocolServiceCoroutineImplBase() {
    override suspend fun createDraft(request: CreateDraftRequest): ProtocolDraftRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            service.createDraft(
                tenantId = resolveTenant(request.tenantId, principal),
                protocolId = request.protocolId,
                title = request.title,
                contentJson = request.contentJson,
                actorId = resolveActor(request.actorId, principal),
            ).toGrpc()
        }
    }

    override suspend fun updateDraft(request: UpdateDraftRequest): ProtocolDraftRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            service.updateDraft(
                tenantId = resolveTenant(request.tenantId, principal),
                protocolId = request.protocolId,
                title = request.title,
                contentJson = request.contentJson,
                actorId = resolveActor(request.actorId, principal),
            ).toGrpc()
        }
    }

    override suspend fun publishVersion(request: PublishVersionRequest): ProtocolVersionRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            val tenantId = resolveTenant(request.tenantId, principal)

            service.publishVersion(
                tenantId = tenantId,
                protocolId = request.protocolId,
                actorId = resolveActor(request.actorId, principal),
                changelog = request.changelog,
                signature = null, // Will be handled by ESignatureService if needed
            ).toGrpc()
        }
    }

    override suspend fun listProtocols(request: ListProtocolsRequest): ListProtocolsResponse {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val protocols =
                service.listProtocols(
                    tenantId = resolveTenant(request.tenantId, principal),
                    limit = request.limit,
                )
            ListProtocolsResponse.newBuilder()
                .addAllProtocols(protocols.map { it.toGrpc() })
                .build()
        }
    }

    override suspend fun getProtocolVersion(request: GetProtocolVersionRequest): ProtocolVersionRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            service.getProtocolVersion(
                tenantId = resolveTenant(request.tenantId, principal),
                protocolId = request.protocolId,
                version = request.version,
            ).toGrpc()
        }
    }

    override suspend fun diffVersions(request: DiffVersionsRequest): DiffVersionsResponse {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val diff =
                service.diffVersions(
                    tenantId = resolveTenant(request.tenantId, principal),
                    protocolId = request.protocolId,
                    baseVersion = request.baseVersion,
                    targetVersion = request.targetVersion,
                )
            DiffVersionsResponse.newBuilder()
                .setSummary(diff.summary)
                .addAllAddedLines(diff.addedLines)
                .addAllRemovedLines(diff.removedLines)
                .build()
        }
    }
}

class RegenRunGrpcService(
    private val service: RegenOpsService,
) : RunServiceGrpcKt.RunServiceCoroutineImplBase() {
    override suspend fun startRun(request: StartRunRequest): RunRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            service.startRun(
                tenantId = resolveTenant(request.tenantId, principal),
                protocolId = request.protocolId,
                protocolVersion = request.protocolVersion,
                runId = request.runId,
                gatewayId = request.gatewayId,
                actorId = resolveActor(request.actorId, principal),
            ).toGrpc()
        }
    }

    override suspend fun pauseRun(request: RunControlRequest): RunRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            service.pauseRun(
                tenantId = resolveTenant(request.tenantId, principal),
                runId = request.runId,
                actorId = resolveActor(request.actorId, principal),
                reason = request.reason,
            ).toGrpc()
        }
    }

    override suspend fun abortRun(request: RunControlRequest): RunRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            service.abortRun(
                tenantId = resolveTenant(request.tenantId, principal),
                runId = request.runId,
                actorId = resolveActor(request.actorId, principal),
                reason = request.reason,
            ).toGrpc()
        }
    }

    override suspend fun getRun(request: GetRunRequest): RunRecord {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            service.getRun(
                tenantId = resolveTenant(request.tenantId, principal),
                runId = request.runId,
            ).toGrpc()
        }
    }

    override fun streamRunEvents(request: StreamRunEventsRequest): Flow<RunEventRecord> {
        val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor", "gateway")
        val tenantId = resolveTenant(request.tenantId, principal)
        val events = service.streamRunEvents(tenantId, request.runId, request.sinceMs, request.sinceSeq, request.limit)
        return events.asFlow().map { it.toGrpc() }
    }

    override fun streamTelemetry(request: StreamTelemetryRequest): Flow<TelemetryRecord> {
        val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor", "gateway")
        val tenantId = resolveTenant(request.tenantId, principal)
        val telemetry = service.streamTelemetry(tenantId, request.runId, request.sinceMs, request.sinceSeq, request.limit)
        return telemetry.asFlow().map { it.toGrpc() }
    }
}

class RegenGatewayGrpcService(
    private val service: RegenOpsService,
) : com.neogenesis.grpc.GatewayServiceGrpcKt.GatewayServiceCoroutineImplBase() {
    override suspend fun registerGateway(request: RegisterGatewayRequest): GatewayRecord {
        return grpcCall {
            val principal = requireGrpcGrant("gateway", "regenops_operator", "admin")
            requireGatewayMutualTls(principal)
            val certificateSerial = resolveGatewayCertificateSerial(request.certificateSerial, principal)
            service.registerGateway(
                tenantId = resolveTenant(request.tenantId, principal),
                gatewayId = request.gatewayId,
                displayName = request.displayName,
                certificateSerial = certificateSerial,
            ).toGrpc()
        }
    }

    override suspend fun heartbeat(request: HeartbeatRequest): GatewayRecord {
        return grpcCall {
            val principal = requireGrpcGrant("gateway", "regenops_operator", "admin")
            requireGatewayMutualTls(principal)
            val certificateSerial = resolveGatewayCertificateSerial(request.certificateSerial, principal)
            service.heartbeat(
                tenantId = resolveTenant(request.tenantId, principal),
                gatewayId = request.gatewayId,
                certificateSerial = certificateSerial,
            ).toGrpc()
        }
    }

    override suspend fun pushRunEvents(request: PushRunEventsRequest): GatewayAck {
        return grpcCall {
            val principal = requireGrpcGrant("gateway", "regenops_operator", "admin")
            requireGatewayMutualTls(principal)
            val tenantId = resolveTenant(request.tenantId, principal)
            val result =
                service.pushRunEvents(
                    tenantId = tenantId,
                    gatewayId = request.gatewayId,
                    events =
                        request.eventsList.map {
                            RegenRunEvent(
                                tenantId = tenantId,
                                runId = it.runId,
                                eventType = it.eventType,
                                source = "gateway",
                                payloadJson = it.payloadJson,
                                createdAtMs = if (it.createdAtMs <= 0L) System.currentTimeMillis() else it.createdAtMs,
                            )
                        },
                )
            GatewayAck.newBuilder()
                .setAccepted(result.accepted)
                .setRejected(result.rejected)
                .setMessage("ingest_complete")
                .build()
        }
    }

    override suspend fun pushTelemetry(request: PushTelemetryRequest): GatewayAck {
        return grpcCall {
            val principal = requireGrpcGrant("gateway", "regenops_operator", "admin")
            requireGatewayMutualTls(principal)
            val tenantId = resolveTenant(request.tenantId, principal)
            val result =
                service.pushTelemetry(
                    tenantId = tenantId,
                    gatewayId = request.gatewayId,
                    telemetry =
                        request.telemetryList.map {
                            RegenTelemetryPoint(
                                tenantId = tenantId,
                                runId = it.runId,
                                gatewayId = request.gatewayId,
                                metricKey = it.metricKey,
                                metricValue = it.metricValue,
                                unit = it.unit,
                                driftScore = it.driftScore,
                                recordedAtMs = if (it.recordedAtMs <= 0L) System.currentTimeMillis() else it.recordedAtMs,
                            )
                        },
                )
            GatewayAck.newBuilder()
                .setAccepted(result.accepted)
                .setRejected(result.rejected)
                .setMessage("ingest_complete")
                .build()
        }
    }

    override suspend fun fetchConfig(request: FetchConfigRequest): GatewayConfig {
        return grpcCall {
            val principal = requireGrpcGrant("gateway", "regenops_operator", "admin")
            requireGatewayMutualTls(principal)
            val config = service.fetchConfig(resolveTenant(request.tenantId, principal), request.gatewayId)
            GatewayConfig.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .setActiveProtocolId(config.activeProtocolId)
                .setActiveProtocolVersion(config.activeProtocolVersion)
                .setProtocolContentJson(config.protocolContentJson)
                .setIssuedAtMs(config.issuedAtMs)
                .setEmpty(config.empty)
                .build()
        }
    }
}

private fun requireGatewayMutualTls(principal: GrpcPrincipal) {
    if (principal.grants.contains("gateway") && principal.mtlsCertificateSerial.isNullOrBlank()) {
        throw Status.UNAUTHENTICATED.withDescription("gateway principal requires mTLS client certificate").asException()
    }
}

class RegenMetricsGrpcService(
    private val service: RegenOpsService,
) : MetricsServiceGrpcKt.MetricsServiceCoroutineImplBase() {
    override suspend fun getReproducibilityScore(request: GetReproducibilityScoreRequest): ReproducibilityScoreResponse {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val score = service.getReproducibilityScore(resolveTenant(request.tenantId, principal), request.runId)
            ReproducibilityScoreResponse.newBuilder()
                .setTenantId(score.tenantId)
                .setRunId(score.runId)
                .setScore(score.score)
                .setEventCount(score.eventCount)
                .setTelemetryCount(score.telemetryCount)
                .setDriftAlertCount(score.driftAlertCount)
                .build()
        }
    }

    override suspend fun listDriftAlerts(request: ListDriftAlertsRequest): ListDriftAlertsResponse {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val alerts = service.listDriftAlerts(resolveTenant(request.tenantId, principal), request.runId, request.limit)
            ListDriftAlertsResponse.newBuilder()
                .addAllAlerts(
                    alerts.map {
                        com.neogenesis.grpc.DriftAlertRecord.newBuilder()
                            .setTenantId(it.tenantId)
                            .setRunId(it.runId)
                            .setSeverity(it.severity)
                            .setMetricKey(it.metricKey)
                            .setMetricValue(it.metricValue)
                            .setThreshold(it.threshold)
                            .setCreatedAtMs(it.createdAtMs)
                            .build()
                    },
                ).build()
        }
    }

    override suspend fun exportRunReport(request: ExportRunReportRequest): RunReportResponse {
        return grpcCall {
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val report = service.exportRunReport(resolveTenant(request.tenantId, principal), request.runId)
            RunReportResponse.newBuilder()
                .setTenantId(report.tenantId)
                .setRunId(report.runId)
                .setProtocolId(report.protocolId)
                .setProtocolVersion(report.protocolVersion)
                .setStatus(report.status.name)
                .setReproducibilityScore(report.reproducibilityScore)
                .setEventsJson(report.eventsJson)
                .setTelemetryJson(report.telemetryJson)
                .setEvidenceChainValid(report.evidenceChainValid)
                .setGeneratedAtMs(report.generatedAtMs)
                .build()
        }
    }
}

private suspend fun <T> grpcCall(block: suspend () -> T): T {
    return try {
        block()
    } catch (error: Throwable) {
        throw error.toGrpcStatusException()
    }
}

private fun Throwable.toGrpcStatusException(): StatusException {
    if (this is StatusException) {
        return this
    }
    val status =
        when (this) {
            is BadRequestException,
            is IllegalArgumentException,
            -> Status.INVALID_ARGUMENT.withDescription(message ?: "invalid_argument")

            is ConflictException -> Status.ALREADY_EXISTS.withDescription(message ?: "conflict")
            else -> Status.INTERNAL.withDescription(message ?: "internal_server_error")
        }
    return status.withCause(this).asException()
}

private fun resolveTenant(
    tenantFromRequest: String,
    principal: GrpcPrincipal,
): String {
    val requested = tenantFromRequest.trim()
    val fromPrincipal = principal.tenantId?.trim().orEmpty()
    if (fromPrincipal.isNotBlank()) {
        if (requested.isNotBlank() && requested != fromPrincipal) {
            throw Status.PERMISSION_DENIED.withDescription("tenant mismatch").asException()
        }
        return fromPrincipal
    }
    if (requested.isBlank()) {
        throw Status.INVALID_ARGUMENT.withDescription("tenantId is required").asException()
    }
    return requested
}

private fun resolveActor(
    actorFromRequest: String,
    principal: GrpcPrincipal,
): String {
    return actorFromRequest.trim().ifBlank { principal.subject }
}

private fun resolveGatewayCertificateSerial(
    certificateSerialFromRequest: String,
    principal: GrpcPrincipal,
): String {
    val requested = normalizeCertificateSerial(certificateSerialFromRequest)
    val mtlsSerial = normalizeCertificateSerial(principal.mtlsCertificateSerial.orEmpty())
    val isGatewayPrincipal = principal.grants.contains("gateway")

    if (isGatewayPrincipal) {
        if (mtlsSerial.isBlank()) {
            throw Status.UNAUTHENTICATED.withDescription("mTLS client certificate is required for gateway principal").asException()
        }
        if (requested.isNotBlank() && requested != mtlsSerial) {
            throw Status.PERMISSION_DENIED.withDescription("certificate serial mismatch").asException()
        }
        return mtlsSerial
    }

    if (requested.isBlank()) {
        throw Status.INVALID_ARGUMENT.withDescription("certificateSerial is required").asException()
    }
    return requested
}

private fun normalizeCertificateSerial(raw: String): String {
    val trimmed = raw.trim()
    val withoutPrefix =
        if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2)
        } else {
            trimmed
        }
    return withoutPrefix
        .replace(":", "")
        .lowercase()
}

private fun com.neogenesis.server.application.regenops.RegenProtocolDraft.toGrpc(): ProtocolDraftRecord {
    return ProtocolDraftRecord.newBuilder()
        .setTenantId(tenantId)
        .setProtocolId(protocolId)
        .setTitle(title)
        .setContentJson(contentJson)
        .setUpdatedBy(updatedBy)
        .setCreatedAtMs(createdAtMs)
        .setUpdatedAtMs(updatedAtMs)
        .build()
}

private fun com.neogenesis.server.application.regenops.RegenProtocolVersion.toGrpc(): ProtocolVersionRecord {
    return ProtocolVersionRecord.newBuilder()
        .setTenantId(tenantId)
        .setProtocolId(protocolId)
        .setVersion(version)
        .setTitle(title)
        .setContentJson(contentJson)
        .setPublishedBy(publishedBy)
        .setChangelog(changelog)
        .setCreatedAtMs(createdAtMs)
        .build()
}

private fun com.neogenesis.server.application.regenops.RegenProtocolSummary.toGrpc(): ProtocolSummary {
    return ProtocolSummary.newBuilder()
        .setTenantId(tenantId)
        .setProtocolId(protocolId)
        .setTitle(title)
        .setLatestVersion(latestVersion)
        .setHasDraft(hasDraft)
        .setUpdatedAtMs(updatedAtMs)
        .build()
}

private fun com.neogenesis.server.application.regenops.RegenRun.toGrpc(): RunRecord {
    return RunRecord.newBuilder()
        .setTenantId(tenantId)
        .setRunId(runId)
        .setProtocolId(protocolId)
        .setProtocolVersion(protocolVersion)
        .setStatus(status.name)
        .setGatewayId(gatewayId.orEmpty())
        .setStartedBy(startedBy)
        .setStartedAtMs(startedAtMs)
        .setUpdatedAtMs(updatedAtMs)
        .setPausedAtMs(pausedAtMs ?: 0)
        .setAbortedAtMs(abortedAtMs ?: 0)
        .setAbortReason(abortReason.orEmpty())
        .build()
}

private fun RegenRunEvent.toGrpc(): RunEventRecord {
    return RunEventRecord.newBuilder()
        .setTenantId(tenantId)
        .setRunId(runId)
        .setSeq(seq)
        .setEventType(eventType)
        .setSource(source)
        .setPayloadJson(payloadJson)
        .setCreatedAtMs(createdAtMs)
        .build()
}

private fun RegenTelemetryPoint.toGrpc(): TelemetryRecord {
    return TelemetryRecord.newBuilder()
        .setTenantId(tenantId)
        .setRunId(runId)
        .setSeq(seq)
        .setGatewayId(gatewayId)
        .setMetricKey(metricKey)
        .setMetricValue(metricValue)
        .setUnit(unit)
        .setDriftScore(driftScore)
        .setRecordedAtMs(recordedAtMs)
        .build()
}

private fun com.neogenesis.server.application.regenops.RegenGateway.toGrpc(): GatewayRecord {
    return GatewayRecord.newBuilder()
        .setTenantId(tenantId)
        .setGatewayId(gatewayId)
        .setDisplayName(displayName)
        .setCertificateSerial(certificateSerial)
        .setStatus(status)
        .setLastHeartbeatAtMs(lastHeartbeatAtMs ?: 0)
        .setUpdatedAtMs(updatedAtMs)
        .build()
}
