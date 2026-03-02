@file:Suppress("ktlint:standard:import-ordering")

package com.neogenesis.server.infrastructure.grpc.regenops

import com.neogenesis.platform.proto.v1.AbortRunRequest
import com.neogenesis.platform.proto.v1.GetRunRequest
import com.neogenesis.platform.proto.v1.ListProtocolsRequest
import com.neogenesis.platform.proto.v1.ListProtocolsResponse
import com.neogenesis.platform.proto.v1.PauseRunRequest
import com.neogenesis.platform.proto.v1.ProtocolServiceGrpcKt
import com.neogenesis.platform.proto.v1.ProtocolSummary
import com.neogenesis.platform.proto.v1.ProtocolVersion
import com.neogenesis.platform.proto.v1.PublishVersionRequest
import com.neogenesis.platform.proto.v1.RunEvent
import com.neogenesis.platform.proto.v1.RunRef
import com.neogenesis.platform.proto.v1.RunServiceGrpcKt
import com.neogenesis.platform.proto.v1.StartRunRequest
import com.neogenesis.platform.proto.v1.TelemetryFrame
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenRunEvent
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import com.neogenesis.server.domain.device.Capability
import com.neogenesis.server.infrastructure.grpc.GrpcCapabilityGuard
import com.neogenesis.server.infrastructure.grpc.GrpcPrincipal
import com.neogenesis.server.infrastructure.grpc.requireGrpcGrant
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RegenProtocolV1GrpcService(
    private val service: RegenOpsService,
) : ProtocolServiceGrpcKt.ProtocolServiceCoroutineImplBase() {
    override suspend fun listProtocols(request: ListProtocolsRequest): ListProtocolsResponse {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.READ_ONLY_DASHBOARD)
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val tenantId = requireTenant(principal)
            val protocols = service.listProtocols(tenantId = tenantId, limit = 100)
            ListProtocolsResponse.newBuilder()
                .addAllProtocols(
                    protocols.map { summary ->
                        val latest =
                            if (summary.latestVersion > 0) {
                                service.getProtocolVersion(tenantId, summary.protocolId, summary.latestVersion)
                            } else {
                                null
                            }
                        ProtocolSummary.newBuilder()
                            .setProtocolId(summary.protocolId)
                            .setName(summary.title)
                            .setSummary("")
                            .apply {
                                if (latest != null) {
                                    latestVersion = latest.toV1(versionId = latest.version.toString())
                                }
                            }
                            .build()
                    },
                ).build()
        }
    }

    override suspend fun publishVersion(request: PublishVersionRequest): ProtocolVersion {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.PROTOCOL_EDIT)
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            val tenantId = requireTenant(principal)
            val requestedVersionId = request.versionId.trim()
            val requestedVersion = parseVersionId(requestedVersionId) ?: 1
            val published =
                service.publishVersion(
                    tenantId = tenantId,
                    protocolId = request.protocolId,
                    actorId = principal.subject,
                    changelog = "published",
                    signature = null,
                )
            published.toV1(
                versionId = if (requestedVersionId.isNotBlank()) requestedVersionId else published.version.toString(),
                versionString = requestedVersion.toString(),
            )
        }
    }
}

class RegenRunV1GrpcService(
    private val service: RegenOpsService,
) : RunServiceGrpcKt.RunServiceCoroutineImplBase() {
    override suspend fun startRun(request: StartRunRequest): RunRef {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.PRINT_CONTROL)
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            val tenantId = requireTenant(principal)
            val version = parseVersionId(request.versionId) ?: 1
            val created =
                service.startRun(
                    tenantId = tenantId,
                    protocolId = request.protocolId,
                    protocolVersion = version,
                    runId = "",
                    gatewayId = null,
                    actorId = principal.subject,
                )
            RunRef.newBuilder()
                .setRunId(created.runId)
                .setStatus(created.status.name)
                .build()
        }
    }

    override suspend fun pauseRun(request: PauseRunRequest): RunRef {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.PRINT_CONTROL)
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            val tenantId = requireTenant(principal)
            val updated =
                service.pauseRun(
                    tenantId = tenantId,
                    runId = request.runId,
                    actorId = principal.subject,
                    reason = "paused",
                )
            RunRef.newBuilder()
                .setRunId(updated.runId)
                .setStatus(updated.status.name)
                .build()
        }
    }

    override suspend fun abortRun(request: AbortRunRequest): RunRef {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.PRINT_CONTROL)
            val principal = requireGrpcGrant("regenops_operator", "admin", "operator")
            val tenantId = requireTenant(principal)
            val updated =
                service.abortRun(
                    tenantId = tenantId,
                    runId = request.runId,
                    actorId = principal.subject,
                    reason = "aborted",
                )
            RunRef.newBuilder()
                .setRunId(updated.runId)
                .setStatus(updated.status.name)
                .build()
        }
    }

    override suspend fun getRun(request: GetRunRequest): RunRef {
        return grpcCall {
            GrpcCapabilityGuard.requireCapability(Capability.QC_REVIEW)
            val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor")
            val tenantId = requireTenant(principal)
            val run = service.getRun(tenantId = tenantId, runId = request.runId)
            RunRef.newBuilder()
                .setRunId(run.runId)
                .setStatus(run.status.name)
                .build()
        }
    }

    override fun streamRunEvents(request: GetRunRequest): Flow<RunEvent> {
        GrpcCapabilityGuard.requireCapability(Capability.QC_REVIEW)
        val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor", "gateway")
        val tenantId = requireTenant(principal)
        val events = service.streamRunEvents(tenantId, request.runId, 0, 0, 250)
        return events.asFlow().map { it.toV1() }
    }

    override fun streamTelemetry(request: GetRunRequest): Flow<TelemetryFrame> {
        GrpcCapabilityGuard.requireCapability(Capability.LIVE_MONITOR)
        val principal = requireGrpcGrant("regenops_operator", "regenops_auditor", "admin", "auditor", "gateway")
        val tenantId = requireTenant(principal)
        val telemetry = service.streamTelemetry(tenantId, request.runId, 0, 0, 250)
        return telemetry.asFlow().map { it.toV1Telemetry() }
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

private fun requireTenant(principal: GrpcPrincipal): String {
    val tenantId = principal.tenantId?.trim().orEmpty()
    if (tenantId.isBlank()) {
        throw Status.INVALID_ARGUMENT.withDescription("tenantId is required").asException()
    }
    return tenantId
}

private fun parseVersionId(raw: String): Int? {
    val digits = raw.filter { it.isDigit() }
    return digits.toIntOrNull()
}

private fun com.neogenesis.server.application.regenops.RegenProtocolVersion.toV1(
    versionId: String,
    versionString: String = version.toString(),
): ProtocolVersion {
    return ProtocolVersion.newBuilder()
        .setVersionId(versionId)
        .setProtocolId(protocolId)
        .setVersion(versionString)
        .setCreatedAt(Instant.ofEpochMilli(createdAtMs).toString())
        .setPayload(contentJson)
        .setPublished(true)
        .build()
}

private fun RegenRunEvent.toV1(): RunEvent {
    return RunEvent.newBuilder()
        .setRunId(runId)
        .setEventType(eventType)
        .setMessage(payloadJson)
        .setCreatedAt(Instant.ofEpochMilli(createdAtMs).toString())
        .build()
}

private fun RegenTelemetryPoint.toV1Telemetry(): TelemetryFrame {
    val frame =
        TelemetryFrame.newBuilder()
            .setJobId(runId)
            .setDeviceId(gatewayId)
            .setTimestampMs(recordedAtMs)
    when (normalizeMetricKey(metricKey)) {
        "pressure_kpa" -> frame.pressureKpa = metricValue
        "displacement_um" -> frame.displacementUm = metricValue
        "flow_rate_ul_s" -> frame.flowRateUlS = metricValue
        "temperature_c" -> frame.temperatureC = metricValue
        "viscosity_pas" -> frame.viscosityPas = metricValue
        "pid_p" -> frame.pidP = metricValue
        "pid_i" -> frame.pidI = metricValue
        "pid_d" -> frame.pidD = metricValue
        "mpc_horizon_ms" -> frame.mpcHorizonMs = metricValue.toInt()
        "mpc_predicted_pressure_kpa" -> frame.mpcPredictedPressureKpa = metricValue
    }
    return frame.build()
}

private fun normalizeMetricKey(raw: String): String {
    return raw.trim()
        .lowercase()
        .replace('.', '_')
}
