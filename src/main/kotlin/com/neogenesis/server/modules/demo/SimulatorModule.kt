package com.neogenesis.server.modules.demo

import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenOpsStore
import com.neogenesis.server.application.regenops.RegenRunEvent
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.modules.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun Route.simulatorModule(
    regenOpsService: RegenOpsService,
    regenOpsStore: RegenOpsStore,
    complianceEnabled: Boolean,
    driftThreshold: Double = 0.2,
) {
    authenticate("auth-jwt") {
        post("/demo/simulator/runs") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            if (complianceEnabled) {
                throw ApiException(
                    "compliance_mode_enabled",
                    "Simulator publishing is disabled when COMPLIANCE_MODE=true",
                    HttpStatusCode.Conflict,
                )
            }
            val tenantId = call.requireTenantId()
            val correlationId = call.requireCorrelationId()
            val actorId = call.actor()
            val request = call.receive<SimulatorStartRequest>()
            val protocolId = request.protocolId?.trim().orEmpty().ifBlank { "sim-protocol" }
            val runId = request.runId?.trim().orEmpty().ifBlank { "sim-${System.currentTimeMillis()}" }
            val samples = request.samples.coerceIn(20, 5_000)
            val intervalMs = request.intervalMs.coerceIn(200, 10_000)
            val failureAt = request.failureAt?.coerceIn(5, samples - 1)

            val contentJson =
                buildJsonObject {
                    put("title", "Simulated Protocol")
                    put("baselinePressureKpa", 90.0)
                    put("baselineTempC", 36.5)
                }.toString()

            runCatching {
                regenOpsService.createDraft(
                    tenantId = tenantId,
                    protocolId = protocolId,
                    title = "Simulated Protocol",
                    contentJson = contentJson,
                    actorId = actorId,
                )
            }.recoverCatching { error ->
                if (error is ConflictException) {
                    regenOpsService.updateDraft(
                        tenantId = tenantId,
                        protocolId = protocolId,
                        title = "Simulated Protocol",
                        contentJson = contentJson,
                        actorId = actorId,
                    )
                } else {
                    throw error
                }
            }.getOrElse { error ->
                if (error is BadRequestException) {
                    throw ApiException(error.code, error.message, HttpStatusCode.BadRequest)
                }
                throw error
            }

            val version =
                regenOpsService.publishVersion(
                    tenantId = tenantId,
                    protocolId = protocolId,
                    actorId = actorId,
                    changelog = "simulator publish",
                )

            val run =
                regenOpsService.startRun(
                    tenantId = tenantId,
                    protocolId = protocolId,
                    protocolVersion = version.version,
                    runId = runId,
                    gatewayId = "simulator",
                    actorId = actorId,
                )

            val generator =
                SimulatorGenerator(
                    tenantId = tenantId,
                    runId = run.runId,
                    gatewayId = "simulator",
                    baselinePressure = 90.0,
                    baselineTemp = 36.5,
                    driftThreshold = driftThreshold,
                    failureAt = failureAt,
                )
            val now = System.currentTimeMillis()
            val result = generator.generate(samples, intervalMs, now)

            result.events.forEach { event ->
                regenOpsStore.appendRunEvent(
                    tenantId = tenantId,
                    runId = event.runId,
                    eventType = event.eventType,
                    source = event.source,
                    payloadJson = event.payloadJson,
                    createdAtMs = event.createdAtMs,
                )
            }
            if (result.telemetry.isNotEmpty()) {
                regenOpsStore.appendTelemetry(result.telemetry)
            }
            result.alerts.forEach { regenOpsStore.insertDriftAlert(it) }

            call.respond(
                SimulatorRunResponse(
                    tenantId = tenantId,
                    correlationId = correlationId,
                    runId = run.runId,
                    protocolId = protocolId,
                    protocolVersion = version.version,
                    samples = samples,
                    failureInjected = failureAt != null,
                ),
            )
        }

        get("/demo/simulator/runs/{runId}/events") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            val runId = call.parameters["runId"]?.trim().orEmpty()
            val sinceMs = call.request.queryParameters["sinceMs"]?.toLongOrNull() ?: 0L
            val sinceSeq = call.request.queryParameters["sinceSeq"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 250
            val events = regenOpsStore.listRunEvents(tenantId, runId, sinceMs, sinceSeq, limit)
            call.respond(SimulatorEventsResponse(events = events))
        }

        get("/demo/simulator/runs/{runId}/telemetry") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            val runId = call.parameters["runId"]?.trim().orEmpty()
            val sinceMs = call.request.queryParameters["sinceMs"]?.toLongOrNull() ?: 0L
            val sinceSeq = call.request.queryParameters["sinceSeq"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 250
            val telemetry = regenOpsStore.listTelemetry(tenantId, runId, sinceMs, sinceSeq, limit)
            call.respond(SimulatorTelemetryResponse(telemetry = telemetry))
        }
    }
}

@Serializable
data class SimulatorStartRequest(
    val protocolId: String? = null,
    val runId: String? = null,
    val samples: Int = 120,
    val intervalMs: Long = 1_000,
    val failureAt: Int? = 90,
)

@Serializable
data class SimulatorRunResponse(
    val tenantId: String,
    val correlationId: String,
    val runId: String,
    val protocolId: String,
    val protocolVersion: Int,
    val samples: Int,
    val failureInjected: Boolean,
)

@Serializable
data class SimulatorEventsResponse(
    val events: List<RegenRunEvent>,
)

@Serializable
data class SimulatorTelemetryResponse(
    val telemetry: List<RegenTelemetryPoint>,
)

private data class SimulatorBatch(
    val events: List<RegenRunEvent>,
    val telemetry: List<RegenTelemetryPoint>,
    val alerts: List<com.neogenesis.server.application.regenops.RegenDriftAlert>,
)

private class SimulatorGenerator(
    private val tenantId: String,
    private val runId: String,
    private val gatewayId: String,
    private val baselinePressure: Double,
    private val baselineTemp: Double,
    private val driftThreshold: Double,
    private val failureAt: Int?,
) {
    fun generate(
        samples: Int,
        intervalMs: Long,
        startMs: Long,
    ): SimulatorBatch {
        val events = mutableListOf<RegenRunEvent>()
        val telemetry = mutableListOf<RegenTelemetryPoint>()
        val alerts = mutableListOf<com.neogenesis.server.application.regenops.RegenDriftAlert>()

        events += buildEvent("sim.run.initialized", startMs, "{\"phase\":\"init\"}")
        var failureInjected = false

        for (index in 0 until samples) {
            val timestamp = startMs + (index * intervalMs)
            val phase = when {
                index < samples / 3 -> "stabilizing"
                index < (samples * 2 / 3) -> "printing"
                else -> "cooldown"
            }
            if (index % 20 == 0) {
                events += buildEvent("sim.phase", timestamp, "{\"phase\":\"$phase\"}")
            }
            val driftMultiplier = if (failureAt != null && index >= failureAt) 1.0 + ((index - failureAt) * 0.02) else 1.0
            if (failureAt != null && index == failureAt && !failureInjected) {
                failureInjected = true
                events += buildEvent("sim.failure.injected", timestamp, "{\"type\":\"pressure_spike\"}")
            }
            val pressure = baselinePressure * driftMultiplier + sin(index / 6.0) * 0.8
            val temp = baselineTemp + sin(index / 8.0) * 0.4 + (if (failureInjected) 0.8 else 0.0)
            val driftScore = computeDriftScore(pressure, baselinePressure)
            telemetry +=
                RegenTelemetryPoint(
                    tenantId = tenantId,
                    runId = runId,
                    gatewayId = gatewayId,
                    metricKey = "pressure_kpa",
                    metricValue = pressure,
                    unit = "kPa",
                    driftScore = driftScore,
                    recordedAtMs = timestamp,
                )
            telemetry +=
                RegenTelemetryPoint(
                    tenantId = tenantId,
                    runId = runId,
                    gatewayId = gatewayId,
                    metricKey = "temperature_c",
                    metricValue = temp,
                    unit = "C",
                    driftScore = computeDriftScore(temp, baselineTemp),
                    recordedAtMs = timestamp,
                )
            if (driftScore >= driftThreshold) {
                alerts +=
                    com.neogenesis.server.application.regenops.RegenDriftAlert(
                        tenantId = tenantId,
                        runId = runId,
                        severity = if (driftScore >= 0.5) "critical" else "warning",
                        metricKey = "pressure_kpa",
                        metricValue = pressure,
                        threshold = driftThreshold,
                        createdAtMs = timestamp,
                    )
            }
        }
        events += buildEvent("sim.run.complete", startMs + (samples * intervalMs), "{\"samples\":$samples}")
        return SimulatorBatch(events = events, telemetry = telemetry, alerts = alerts)
    }

    private fun buildEvent(type: String, createdAtMs: Long, payloadJson: String): RegenRunEvent {
        return RegenRunEvent(
            tenantId = tenantId,
            runId = runId,
            eventType = type,
            source = "simulator",
            payloadJson = payloadJson,
            createdAtMs = createdAtMs,
        )
    }

    private fun computeDriftScore(value: Double, baseline: Double): Double {
        val delta = abs(value - baseline)
        return min(1.0, max(0.0, delta / max(0.1, baseline)))
    }
}

private fun io.ktor.server.application.ApplicationCall.requireCorrelationId(): String {
    val correlationId =
        request.headers["X-Correlation-Id"]
            ?: request.headers["X-Request-Id"]
    if (correlationId.isNullOrBlank()) {
        throw ApiException("correlation_required", "correlation_id is required", HttpStatusCode.BadRequest)
    }
    return correlationId
}

private fun io.ktor.server.application.ApplicationCall.requireTenantId(): String {
    val tenantId = request.queryParameters["tenant_id"]
    if (tenantId.isNullOrBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    return tenantId
}
