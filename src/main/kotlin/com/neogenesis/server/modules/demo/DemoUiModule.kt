package com.neogenesis.server.modules.demo

import com.neogenesis.server.application.regenops.RegenOpsService
import com.neogenesis.server.application.regenops.RegenOpsStore
import com.neogenesis.server.application.regenops.RegenRunEvent
import com.neogenesis.server.application.regenops.RegenTelemetryPoint
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.modules.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

fun Route.demoUiModule(
    regenOpsService: RegenOpsService,
    regenOpsStore: RegenOpsStore,
) {
    authenticate("auth-jwt") {
        get("/api/v1/regenops/protocols") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val protocols = DemoProtocolStore.list(tenantId)
            call.respond(ListProtocolsResponse(protocols = protocols))
        }

        post("/api/v1/regenops/protocols") {
        post("/api/v1/regenops/protocols/{protocolId}/status") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val protocolId = call.parameters["protocolId"]?.trim().orEmpty()
            if (protocolId.isBlank()) throw ApiException("protocol_required", "protocolId is required", HttpStatusCode.BadRequest)
            val request = call.receive<UpdateProtocolStatusRequest>()
            val updated = DemoProtocolStore.updateStatus(tenantId, protocolId, request.status)
            call.respond(updated)
        }

        
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val request = call.receive<CreateProtocolRequest>()
            val created = DemoProtocolStore.create(tenantId, request)
            call.respond(created)
        }

        get("/api/v1/metrics/reproducibility-score") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val runId = call.request.queryParameters["run_id"]?.trim().orEmpty()
            val score =
                if (runId.isNotBlank()) {
                    (regenOpsService.getReproducibilityScore(tenantId, runId).score * 100).roundToInt()
                } else {
                    demoScoreForTenant(tenantId)
                }
            call.respond(ReproducibilityScoreResponse(score = score.coerceIn(0, 100)))
        }

        get("/api/v1/metrics/drift-alerts") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val runId = call.request.queryParameters["run_id"]?.trim().orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200
            val alerts =
                if (runId.isNotBlank()) {
                    regenOpsStore.listDriftAlerts(tenantId, runId, limit).map { alert ->
                        DriftAlertResponse(
                            id = "drift-${alert.runId}-${alert.createdAtMs}",
                            title = "Drift detected: ${alert.metricKey}",
                            severity = alert.severity.uppercase(),
                            message = "Metric ${alert.metricKey}=${alert.metricValue} exceeded threshold ${alert.threshold}.",
                            createdAt = alert.createdAtMs,
                        )
                    }
                } else {
                    emptyList()
                }
            val resolved =
                if (alerts.isNotEmpty()) {
                    alerts
                } else {
                    listOf(
                        DriftAlertResponse(
                            id = "drift-demo-1",
                            title = "Pressure variance",
                            severity = "MEDIUM",
                            message = "Simulated runs show pressure variance above baseline.",
                            createdAt = System.currentTimeMillis(),
                        ),
                        DriftAlertResponse(
                            id = "drift-demo-2",
                            title = "Thermal slope",
                            severity = "LOW",
                            message = "Thermal slope trending above baseline in cooldown phase.",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            call.respond(DriftAlertsResponse(alerts = resolved))
        }

        get("/api/v1/commercial/pipeline") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            call.respond(CommercialPipelineResponse(stages = demoPipeline(tenantId)))
        }

        get("/api/v1/commercial/pipeline/export") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val csv = buildPipelineCsv(demoPipeline(tenantId))
            call.response.header("Content-Disposition", "attachment; filename=\"commercial_pipeline.csv\"")
            call.respondText(csv, ContentType.Text.CSV)
        }

        get("/api/v1/telemetry/{runId}/export") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.OPERATOR, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val runId = call.parameters["runId"]?.trim().orEmpty()
            if (runId.isBlank()) {
                throw ApiException("run_required", "runId is required", HttpStatusCode.BadRequest)
            }
            val telemetry = regenOpsStore.listTelemetry(tenantId, runId, 0L, 0L, 10_000)
            val csv = telemetryToCsv(telemetry)
            call.response.header("Content-Disposition", "attachment; filename=\"run_report_${sanitize(runId)}.csv\"")
            call.respondText(csv, ContentType.Text.CSV)
        }

        get("/api/v1/evidence/{runId}/package") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val tenantId = call.requireTenantId()
            call.requireCorrelationId()
            val runId = call.parameters["runId"]?.trim().orEmpty()
            if (runId.isBlank()) {
                throw ApiException("run_required", "runId is required", HttpStatusCode.BadRequest)
            }

            val events = regenOpsStore.listRunEvents(tenantId, runId, 0L, 0L, 10_000)
            val telemetry = regenOpsStore.listTelemetry(tenantId, runId, 0L, 0L, 10_000)
            val report = buildRunReport(tenantId, runId, events, telemetry, regenOpsService)
            val bundle = buildEvidenceBundle(report, events, telemetry)

            call.response.header("Content-Disposition", "attachment; filename=\"evidence_${sanitize(runId)}.zip\"")
            call.respondBytes(bundle, ContentType.Application.Zip)
        }
    }
}

@Serializable
data class ReproducibilityScoreResponse(
    val score: Int,
)

@Serializable
data class DriftAlertResponse(
    val id: String,
    val title: String,
    val severity: String,
    val message: String,
    val createdAt: Long? = null,
)

@Serializable
data class DriftAlertsResponse(
    val alerts: List<DriftAlertResponse>,
)

@Serializable
data class CommercialPipelineResponse(
    val stages: Map<String, List<CommercialOpportunityResponse>>,
)

@Serializable
data class CommercialOpportunityResponse(
    val id: String,
    val name: String,
    val stage: String,
    val expectedRevenueEur: Double,
    val probability: Int,
    val notes: String,
    val loiSigned: Boolean,
)

@Serializable
data class ListProtocolsResponse(
    val protocols: List<ProtocolSummaryResponse> = emptyList(),
)

@Serializable
data class ProtocolSummaryResponse(
    val protocolId: String,
    val title: String,
    val summary: String,
    val latestVersion: Int,
    val status: String? = null,
    val status: String? = null,
    val resultSummary: String? = null,
    val lastOutcome: String? = null,
    val resultMetrics: Map<String, String> = emptyMap(),
    val evidenceSummary: String? = null,
    val lastRunTimeline: List<String> = emptyList(),
    val evidenceArtifacts: List<String> = emptyList(),
    val lastRunId: String? = null,
)

@Serializable
data class CreateProtocolRequest(
    val protocolId: String,
    val title: String,
    val summary: String,
    val contentJson: String,
    val author: String,
    val status: String? = null,
    val resultSummary: String? = null,
    val lastOutcome: String? = null,
    val resultMetrics: Map<String, String> = emptyMap(),
    val evidenceSummary: String? = null,
    val lastRunTimeline: List<String> = emptyList(),
    val evidenceArtifacts: List<String> = emptyList(),
    val lastRunId: String? = null,
)

@Serializable
private data class DemoRunReport(
    val tenantId: String,
    val runId: String,
    val generatedAt: String,
    val eventCount: Int,
    val telemetryCount: Int,
    val reproducibilityScore: Int,
)

@Serializable
private data class EvidenceManifestEntry(
    val file: String,
    val sha256: String,
    val size: Long,
)

@Serializable
private data class EvidenceBundleManifest(
    val version: String,
    val generatedAt: String,
    val entries: List<EvidenceManifestEntry>,
)

private fun demoScoreForTenant(tenantId: String): Int {
    val base = 88
    val variance = abs(tenantId.hashCode()) % 10
    return (base + variance).coerceIn(70, 99)
}

private fun demoPipeline(tenantId: String): Map<String, List<CommercialOpportunityResponse>> {
    val seed = abs(tenantId.hashCode()) % 100
    return linkedMapOf(
        "Discovery" to
            listOf(
                CommercialOpportunityResponse(
                    id = "opp-${seed}01",
                    name = "Nova BioFab Pilot",
                    stage = "Discovery",
                    expectedRevenueEur = 120_000.0,
                    probability = 35,
                    notes = "Pilot scope for regenerative scaffold trials.",
                    loiSigned = false,
                ),
            ),
        "Pilot" to
            listOf(
                CommercialOpportunityResponse(
                    id = "opp-${seed}02",
                    name = "Helix Research Expansion",
                    stage = "Pilot",
                    expectedRevenueEur = 310_000.0,
                    probability = 55,
                    notes = "Expand protocol validation to multi-site runbooks.",
                    loiSigned = true,
                ),
            ),
        "LOI" to
            listOf(
                CommercialOpportunityResponse(
                    id = "opp-${seed}03",
                    name = "Atlas Medical LOI",
                    stage = "LOI",
                    expectedRevenueEur = 640_000.0,
                    probability = 68,
                    notes = "Awaiting regulatory alignment for clinical deployment.",
                    loiSigned = true,
                ),
            ),
        "Contract" to
            listOf(
                CommercialOpportunityResponse(
                    id = "opp-${seed}04",
                    name = "Eden Biologics Contract",
                    stage = "Contract",
                    expectedRevenueEur = 1_250_000.0,
                    probability = 82,
                    notes = "Final contract review pending procurement sign-off.",
                    loiSigned = true,
                ),
            ),
    )
}

private fun buildPipelineCsv(pipeline: Map<String, List<CommercialOpportunityResponse>>): String {
    val header = "id,name,stage,expectedRevenueEur,probability,loiSigned,notes"
    val rows =
        pipeline.values.flatten().joinToString("\n") { opp ->
            listOf(
                opp.id,
                opp.name,
                opp.stage,
                opp.expectedRevenueEur,
                opp.probability,
                opp.loiSigned,
                opp.notes.replace(",", ";"),
            ).joinToString(",")
        }
    return header + "\n" + rows
}

private object DemoProtocolStore {
    private val protocolsByTenant = mutableMapOf<String, MutableList<ProtocolSummaryResponse>>()

    fun list(tenantId: String): List<ProtocolSummaryResponse> {
        val existing = protocolsByTenant.getOrPut(tenantId) {
            mutableListOf(
                ProtocolSummaryResponse(
                    protocolId = "regenops-001",
                    title = "RegenOps: Controlled Growth Run",
                    summary = "Execute a controlled growth simulation with safety bounds and trace checkpoints.",
                    latestVersion = 3,
                    status = "PUBLISHED",
                    resultSummary = "Yield stability 98.7% with zero drift alerts across 3 checkpoints.",
                    lastOutcome = "SUCCESS",
                    resultMetrics = mapOf(
                        "Yield" to "98.7%",
                        "Stability" to "0.3% variance",
                        "Cycle Time" to "42m",
                    ),
                    evidenceSummary = "Evidence bundle sealed with SHA-256 hashes; zero integrity anomalies.",
                    lastRunTimeline = listOf(
                        "00:00 Init safety envelope",
                        "00:17 Checkpoint A verified",
                        "00:29 Checkpoint B verified",
                        "00:41 Completion & seal",
                    ),
                    evidenceArtifacts = listOf("run_report.csv", "audit_bundle.zip", "manifest.json"),
                    lastRunId = "run-demo",
                ),
            )
        }
        return existing.toList()
    }

    fun create(tenantId: String, request: CreateProtocolRequest): ProtocolSummaryResponse {
        val list = protocolsByTenant.getOrPut(tenantId) { mutableListOf() }
        val created = ProtocolSummaryResponse(
            protocolId = request.protocolId.trim(),
            title = request.title.trim(),
            summary = request.summary.trim(),
            latestVersion = 1,
            resultSummary = request.resultSummary,
            lastOutcome = request.lastOutcome,
            resultMetrics = request.resultMetrics,
            evidenceSummary = request.evidenceSummary,
            lastRunTimeline = request.lastRunTimeline,
            evidenceArtifacts = request.evidenceArtifacts,
            lastRunId = request.lastRunId,
            status = request.status,
        )
        list.add(0, created)
        return created
    }
}

private fun telemetryToCsv(telemetry: List<RegenTelemetryPoint>): String {
    val header = "run_id,seq,metric_key,metric_value,unit,drift_score,recorded_at_ms"
    if (telemetry.isEmpty()) return header + "\n"
    val rows =
        telemetry.sortedBy { it.seq }.joinToString("\n") { point ->
            listOf(
                point.runId,
                point.seq,
                point.metricKey,
                point.metricValue,
                point.unit,
                point.driftScore,
                point.recordedAtMs,
            ).joinToString(",")
        }
    return header + "\n" + rows
}

private fun buildRunReport(
    tenantId: String,
    runId: String,
    events: List<RegenRunEvent>,
    telemetry: List<RegenTelemetryPoint>,
    regenOpsService: RegenOpsService,
): DemoRunReport {
    val score =
        runCatching { (regenOpsService.getReproducibilityScore(tenantId, runId).score * 100).roundToInt() }
            .getOrDefault(demoScoreForTenant(tenantId))
    return DemoRunReport(
        tenantId = tenantId,
        runId = runId,
        generatedAt = Instant.now().toString(),
        eventCount = events.size,
        telemetryCount = telemetry.size,
        reproducibilityScore = score.coerceIn(0, 100),
    )
}

private fun buildEvidenceBundle(
    report: DemoRunReport,
    events: List<RegenRunEvent>,
    telemetry: List<RegenTelemetryPoint>,
): ByteArray {
    val files = linkedMapOf<String, ByteArray>()
    val reportJson = Json.encodeToString(report)
    files["run_report.json"] = reportJson.toByteArray(Charsets.UTF_8)
    files["telemetry.csv"] = telemetryToCsv(telemetry).toByteArray(Charsets.UTF_8)
    val eventsJson =
        buildJsonArray {
            events.forEach { event ->
                add(
                    buildJsonObject {
                        put("runId", event.runId)
                        put("seq", event.seq)
                        put("eventType", event.eventType)
                        put("source", event.source)
                        put("payloadJson", event.payloadJson)
                        put("createdAtMs", event.createdAtMs)
                    },
                )
            }
        }.toString()
    files["events.json"] = eventsJson.toByteArray(Charsets.UTF_8)

    val manifestEntries =
        files.map { (name, bytes) ->
            EvidenceManifestEntry(
                file = name,
                sha256 = sha256(bytes),
                size = bytes.size.toLong(),
            )
        }
    val manifest =
        EvidenceBundleManifest(
            version = "1",
            generatedAt = Instant.now().toString(),
            entries = manifestEntries,
        )
    files["manifest.json"] = Json.encodeToString(manifest).toByteArray(Charsets.UTF_8)

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        files.keys.sorted().forEach { name ->
            val bytes = files[name] ?: return@forEach
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun sha256(bytes: ByteArray): String {
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}

private fun sanitize(value: String): String {
    return value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}

private fun io.ktor.server.application.ApplicationCall.requireTenantId(): String {
    val tenantId = request.queryParameters["tenant_id"]
    if (tenantId.isNullOrBlank()) {
        throw ApiException("tenant_required", "tenant_id is required", HttpStatusCode.BadRequest)
    }
    return tenantId
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
