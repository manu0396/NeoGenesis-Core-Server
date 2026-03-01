package com.neogenesis.server.application.regenops

import com.neogenesis.server.application.compliance.ComplianceHooks
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.infrastructure.config.AppConfig
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

class RegenOpsService(
    private val store: RegenOpsStore,
    private val driftThreshold: Double = 0.2,
    private val complianceConfig: AppConfig.ComplianceConfig =
        AppConfig.ComplianceConfig(
            enabled = false,
            retentionDays = 3650,
            wormModeEnabled = false,
            esignEnabled = false,
            scimEnabled = false,
            samlEnabled = false,
        ),
    private val complianceHooks: ComplianceHooks = ComplianceHooks.noop(),
) {
    fun createDraft(
        tenantId: String,
        protocolId: String,
        title: String,
        contentJson: String,
        actorId: String,
    ): RegenProtocolDraft {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedProtocol = normalizeProtocol(protocolId)
        val normalizedActor = normalizeActor(actorId)
        if (store.getDraft(normalizedTenant, normalizedProtocol) != null) {
            throw ConflictException(code = "protocol_draft_exists", message = "Draft already exists")
        }
        return store.createDraft(
            tenantId = normalizedTenant,
            protocolId = normalizedProtocol,
            title = title.trim().ifBlank { normalizedProtocol },
            contentJson = requireContent(contentJson),
            updatedBy = normalizedActor,
        )
    }

    fun updateDraft(
        tenantId: String,
        protocolId: String,
        title: String,
        contentJson: String,
        actorId: String,
    ): RegenProtocolDraft {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedProtocol = normalizeProtocol(protocolId)
        if (store.getDraft(normalizedTenant, normalizedProtocol) == null) {
            throw BadRequestException(code = "protocol_draft_missing", message = "Draft does not exist")
        }
        return store.updateDraft(
            tenantId = normalizedTenant,
            protocolId = normalizedProtocol,
            title = title.trim().ifBlank { normalizedProtocol },
            contentJson = requireContent(contentJson),
            updatedBy = normalizeActor(actorId),
        )
    }

    fun publishVersion(
        tenantId: String,
        protocolId: String,
        actorId: String,
        changelog: String,
        signature: com.neogenesis.server.application.compliance.ESignature? = null,
    ): RegenProtocolVersion {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedProtocol = normalizeProtocol(protocolId)

        if (complianceConfig.esignEnabled && signature == null) {
            throw BadRequestException(code = "esign_required", message = "E-Signature is required for protocol publishing")
        }

        if (complianceConfig.enabled) {
            val approval =
                store.consumePublishApproval(
                    tenantId = normalizedTenant,
                    protocolId = normalizedProtocol,
                    publisherId = normalizeActor(actorId),
                )
                    ?: throw BadRequestException(
                        code = "approval_required",
                        message = "Dual-control approval is required to publish protocol versions",
                    )
            if (approval.approvedBy == actorId) {
                throw BadRequestException(
                    code = "dual_control_violation",
                    message = "Approver must be different from publisher",
                )
            }
            complianceHooks.onApprovalConsumed(approval)
        }
        val draft =
            store.getDraft(normalizedTenant, normalizedProtocol)
                ?: throw BadRequestException(code = "protocol_draft_missing", message = "Draft does not exist")
        val wrappedDsl = wrapProtocolDsl(draft.contentJson)
        if (wrappedDsl.dslVersion == "1") {
            requireValidProtocolDsl(wrappedDsl)
        }
        val nextVersion = store.nextProtocolVersion(normalizedTenant, normalizedProtocol)
        val version =
            store.insertProtocolVersion(
                tenantId = normalizedTenant,
                protocolId = normalizedProtocol,
                version = nextVersion,
                title = draft.title,
                contentJson = if (wrappedDsl.dslVersion == "1") serializeProtocolDsl(wrappedDsl) else draft.contentJson,
                publishedBy = normalizeActor(actorId),
                changelog = changelog.trim(),
            )
        appendEvidence(
            tenantId = normalizedTenant,
            actionType = "protocol.publish_version",
            actorId = normalizeActor(actorId),
            resourceType = "protocol",
            resourceId = normalizedProtocol,
            payloadJson =
                buildJsonObject {
                    put("protocolId", version.protocolId)
                    put("version", version.version)
                    put("changelog", version.changelog)
                }.toString(),
        )
        return version
    }

    fun requestPublishApproval(
        tenantId: String,
        protocolId: String,
        actorId: String,
        reason: String?,
    ): ProtocolPublishApproval {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedProtocol = normalizeProtocol(protocolId)
        val approval =
            store.requestPublishApproval(
                tenantId = normalizedTenant,
                protocolId = normalizedProtocol,
                requestedBy = normalizeActor(actorId),
                reason = reason?.trim()?.ifBlank { null },
            )
        appendEvidence(
            tenantId = normalizedTenant,
            actionType = "protocol.publish.approval.requested",
            actorId = normalizeActor(actorId),
            resourceType = "protocol",
            resourceId = normalizedProtocol,
            payloadJson =
                buildJsonObject {
                    put("approvalId", approval.id)
                    put("protocolId", normalizedProtocol)
                }.toString(),
        )
        complianceHooks.onApprovalRequested(approval)
        return approval
    }

    fun approvePublishApproval(
        tenantId: String,
        approvalId: String,
        actorId: String,
        comment: String?,
    ): ProtocolPublishApproval {
        val normalizedTenant = normalizeTenant(tenantId)
        val approval =
            store.approvePublishApproval(
                tenantId = normalizedTenant,
                approvalId = approvalId.trim(),
                approvedBy = normalizeActor(actorId),
                comment = comment?.trim()?.ifBlank { null },
            )
        appendEvidence(
            tenantId = normalizedTenant,
            actionType = "protocol.publish.approval.approved",
            actorId = normalizeActor(actorId),
            resourceType = "protocol_approval",
            resourceId = approval.id,
            payloadJson =
                buildJsonObject {
                    put("approvalId", approval.id)
                    put("protocolId", approval.protocolId)
                }.toString(),
        )
        complianceHooks.onApprovalApproved(approval)
        return approval
    }

    fun listProtocols(
        tenantId: String,
        limit: Int,
    ): List<RegenProtocolSummary> {
        return store.listProtocols(normalizeTenant(tenantId), sanitizeLimit(limit, 100))
    }

    fun getProtocolVersion(
        tenantId: String,
        protocolId: String,
        version: Int,
    ): RegenProtocolVersion {
        require(version > 0) { "version must be greater than zero" }
        return store.getProtocolVersion(normalizeTenant(tenantId), normalizeProtocol(protocolId), version)
            ?: throw BadRequestException(code = "protocol_version_missing", message = "Version does not exist")
    }

    fun diffVersions(
        tenantId: String,
        protocolId: String,
        baseVersion: Int,
        targetVersion: Int,
    ): RegenVersionDiff {
        val base = getProtocolVersion(tenantId, protocolId, baseVersion)
        val target = getProtocolVersion(tenantId, protocolId, targetVersion)

        val baseLines = base.contentJson.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        val targetLines = target.contentJson.lines().map { it.trimEnd() }.filter { it.isNotBlank() }

        val added = targetLines.filterNot { baseLines.contains(it) }
        val removed = baseLines.filterNot { targetLines.contains(it) }
        val summary = "${added.size} added, ${removed.size} removed"
        return RegenVersionDiff(summary = summary, addedLines = added, removedLines = removed)
    }

    fun startRun(
        tenantId: String,
        protocolId: String,
        protocolVersion: Int,
        runId: String,
        gatewayId: String?,
        actorId: String,
    ): RegenRun {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedProtocol = normalizeProtocol(protocolId)
        require(protocolVersion > 0) { "protocolVersion must be greater than zero" }
        val version = store.getProtocolVersion(normalizedTenant, normalizedProtocol, protocolVersion)
        if (version == null) {
            throw BadRequestException(code = "protocol_version_missing", message = "Cannot start run without published version")
        }

        val normalizedRunId = runId.trim().ifBlank { "run-${System.currentTimeMillis()}" }
        val created =
            store.createRun(
                tenantId = normalizedTenant,
                runId = normalizedRunId,
                protocolId = normalizedProtocol,
                protocolVersion = protocolVersion,
                gatewayId = gatewayId?.trim()?.ifBlank { null },
                startedBy = normalizeActor(actorId),
            )

        store.appendRunEvent(
            tenantId = normalizedTenant,
            runId = created.runId,
            eventType = "run.started",
            source = "server",
            payloadJson =
                buildJsonObject {
                    put("protocolId", normalizedProtocol)
                    put("protocolVersion", protocolVersion)
                }.toString(),
        )
        appendEvidence(
            tenantId = normalizedTenant,
            actionType = "run.start",
            actorId = normalizeActor(actorId),
            resourceType = "run",
            resourceId = created.runId,
            payloadJson =
                buildJsonObject {
                    put("runId", created.runId)
                    put("protocolId", created.protocolId)
                    put("protocolVersion", created.protocolVersion)
                }.toString(),
        )
        return created
    }

    fun pauseRun(
        tenantId: String,
        runId: String,
        actorId: String,
        reason: String,
    ): RegenRun {
        val current = getRun(tenantId, runId)
        if (current.status != RegenRunStatus.RUNNING) {
            throw ConflictException(code = "run_not_running", message = "Only running runs can be paused")
        }
        val updated =
            store.updateRunStatus(
                tenantId = normalizeTenant(tenantId),
                runId = current.runId,
                status = RegenRunStatus.PAUSED,
                reason = reason.trim().ifBlank { "paused" },
            )
        store.appendRunEvent(
            tenantId = current.tenantId,
            runId = current.runId,
            eventType = "run.paused",
            source = "server",
            payloadJson = buildJsonObject { put("reason", reason.trim()) }.toString(),
        )
        appendEvidence(
            tenantId = current.tenantId,
            actionType = "run.pause",
            actorId = normalizeActor(actorId),
            resourceType = "run",
            resourceId = current.runId,
            payloadJson = buildJsonObject { put("reason", reason.trim()) }.toString(),
        )
        return updated
    }

    fun abortRun(
        tenantId: String,
        runId: String,
        actorId: String,
        reason: String,
    ): RegenRun {
        val current = getRun(tenantId, runId)
        if (current.status == RegenRunStatus.ABORTED) {
            throw ConflictException(code = "run_already_aborted", message = "Run already aborted")
        }
        val normalizedReason = reason.trim().ifBlank { "aborted" }
        val updated =
            store.updateRunStatus(
                tenantId = normalizeTenant(tenantId),
                runId = current.runId,
                status = RegenRunStatus.ABORTED,
                reason = normalizedReason,
            )
        store.appendRunEvent(
            tenantId = current.tenantId,
            runId = current.runId,
            eventType = "run.aborted",
            source = "server",
            payloadJson = buildJsonObject { put("reason", normalizedReason) }.toString(),
        )
        appendEvidence(
            tenantId = current.tenantId,
            actionType = "run.abort",
            actorId = normalizeActor(actorId),
            resourceType = "run",
            resourceId = current.runId,
            payloadJson = buildJsonObject { put("reason", normalizedReason) }.toString(),
        )
        return updated
    }

    fun getRun(
        tenantId: String,
        runId: String,
    ): RegenRun {
        return store.getRun(normalizeTenant(tenantId), runId.trim())
            ?: throw BadRequestException(code = "run_missing", message = "Run does not exist")
    }

    fun streamRunEvents(
        tenantId: String,
        runId: String,
        sinceMs: Long,
        sinceSeq: Long,
        limit: Int,
    ): List<RegenRunEvent> {
        return store.listRunEvents(
            normalizeTenant(tenantId),
            runId.trim(),
            max(sinceMs, 0L),
            max(sinceSeq, 0L),
            sanitizeLimit(limit, 250),
        )
    }

    fun streamTelemetry(
        tenantId: String,
        runId: String,
        sinceMs: Long,
        sinceSeq: Long,
        limit: Int,
    ): List<RegenTelemetryPoint> {
        return store.listTelemetry(
            normalizeTenant(tenantId),
            runId.trim(),
            max(sinceMs, 0L),
            max(sinceSeq, 0L),
            sanitizeLimit(limit, 250),
        )
    }

    fun registerGateway(
        tenantId: String,
        gatewayId: String,
        displayName: String,
        certificateSerial: String,
    ): RegenGateway {
        val normalizedTenant = normalizeTenant(tenantId)
        val normalizedGateway =
            gatewayId.trim().ifBlank {
                throw BadRequestException(code = "gateway_id_required", message = "gatewayId is required")
            }
        return store.upsertGateway(
            tenantId = normalizedTenant,
            gatewayId = normalizedGateway,
            displayName = displayName.trim().ifBlank { normalizedGateway },
            certificateSerial = certificateSerial.trim(),
        )
    }

    fun heartbeat(
        tenantId: String,
        gatewayId: String,
        certificateSerial: String,
    ): RegenGateway {
        return store.heartbeatGateway(
            tenantId = normalizeTenant(tenantId),
            gatewayId = gatewayId.trim(),
            certificateSerial = certificateSerial.trim(),
        )
    }

    fun pushRunEvents(
        tenantId: String,
        gatewayId: String,
        events: List<RegenRunEvent>,
    ): IngestResult {
        ensureGatewayExists(tenantId, gatewayId)

        var accepted = 0
        var rejected = 0
        events.forEach { event ->
            val run = store.getRun(normalizeTenant(tenantId), event.runId)
            if (run == null) {
                rejected++
            } else {
                store.appendRunEvent(
                    tenantId = run.tenantId,
                    runId = run.runId,
                    eventType = event.eventType,
                    source = "gateway",
                    payloadJson = event.payloadJson,
                    createdAtMs = event.createdAtMs,
                )
                accepted++
            }
        }
        return IngestResult(accepted = accepted, rejected = rejected)
    }

    fun pushTelemetry(
        tenantId: String,
        gatewayId: String,
        telemetry: List<RegenTelemetryPoint>,
    ): IngestResult {
        ensureGatewayExists(tenantId, gatewayId)

        val accepted = mutableListOf<RegenTelemetryPoint>()
        var rejected = 0
        telemetry.forEach { point ->
            val run = store.getRun(normalizeTenant(tenantId), point.runId)
            if (run == null) {
                rejected++
            } else {
                accepted +=
                    point.copy(
                        tenantId = run.tenantId,
                        gatewayId = gatewayId,
                        recordedAtMs = if (point.recordedAtMs <= 0) System.currentTimeMillis() else point.recordedAtMs,
                    )
            }
        }

        if (accepted.isNotEmpty()) {
            store.appendTelemetry(accepted)
            accepted
                .filter { it.driftScore >= driftThreshold }
                .forEach { point ->
                    store.insertDriftAlert(
                        RegenDriftAlert(
                            tenantId = point.tenantId,
                            runId = point.runId,
                            severity = if (point.driftScore >= 0.5) "critical" else "warning",
                            metricKey = point.metricKey,
                            metricValue = point.metricValue,
                            threshold = driftThreshold,
                            createdAtMs = point.recordedAtMs,
                        ),
                    )
                }
        }

        return IngestResult(accepted = accepted.size, rejected = rejected)
    }

    fun fetchConfig(
        tenantId: String,
        gatewayId: String,
    ): RegenGatewayConfig {
        ensureGatewayExists(tenantId, gatewayId)
        val latest =
            store.latestProtocolVersion(normalizeTenant(tenantId))
                ?: return RegenGatewayConfig(empty = true)
        return RegenGatewayConfig(
            tenantId = latest.tenantId,
            gatewayId = gatewayId,
            activeProtocolId = latest.protocolId,
            activeProtocolVersion = latest.version,
            protocolContentJson = latest.contentJson,
            issuedAtMs = System.currentTimeMillis(),
            empty = false,
        )
    }

    fun getReproducibilityScore(
        tenantId: String,
        runId: String,
    ): RegenReproducibilityScore {
        val run = getRun(tenantId, runId)
        val eventCount = store.countRunEvents(run.tenantId, run.runId)
        val telemetryCount = store.countTelemetry(run.tenantId, run.runId)
        val driftAlertCount = store.listDriftAlerts(run.tenantId, run.runId, 10_000).size
        val driftPenalty = driftAlertCount.toDouble() / max(1, telemetryCount)
        val statusPenalty = if (run.status == RegenRunStatus.ABORTED) 0.15 else 0.0
        val rawScore = 1.0 - driftPenalty - statusPenalty
        return RegenReproducibilityScore(
            tenantId = run.tenantId,
            runId = run.runId,
            score = rawScore.coerceIn(0.0, 1.0),
            eventCount = eventCount,
            telemetryCount = telemetryCount,
            driftAlertCount = driftAlertCount,
        )
    }

    fun listDriftAlerts(
        tenantId: String,
        runId: String,
        limit: Int,
    ): List<RegenDriftAlert> {
        return store.listDriftAlerts(normalizeTenant(tenantId), runId.trim(), sanitizeLimit(limit, 500))
    }

    fun exportRunReport(
        tenantId: String,
        runId: String,
    ): RegenRunReport {
        val run = getRun(tenantId, runId)
        val score = getReproducibilityScore(run.tenantId, run.runId)
        val events = store.listRunEvents(run.tenantId, run.runId, 0, 0, 10_000)
        val telemetry = store.listTelemetry(run.tenantId, run.runId, 0, 0, 10_000)
        val evidenceStatus = store.verifyEvidenceChain(run.tenantId)

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

        val telemetryJson =
            buildJsonArray {
                telemetry.forEach { point ->
                    add(
                        buildJsonObject {
                            put("runId", point.runId)
                            put("seq", point.seq)
                            put("gatewayId", point.gatewayId)
                            put("metricKey", point.metricKey)
                            put("metricValue", point.metricValue)
                            put("unit", point.unit)
                            put("driftScore", point.driftScore)
                            put("recordedAtMs", point.recordedAtMs)
                        },
                    )
                }
            }.toString()

        return RegenRunReport(
            tenantId = run.tenantId,
            runId = run.runId,
            protocolId = run.protocolId,
            protocolVersion = run.protocolVersion,
            status = run.status,
            reproducibilityScore = score.score,
            eventsJson = eventsJson,
            telemetryJson = telemetryJson,
            evidenceChainValid = evidenceStatus.valid,
            generatedAtMs = System.currentTimeMillis(),
        )
    }

    fun verifyEvidenceChain(
        tenantId: String,
        limit: Int = 10_000,
    ): RegenEvidenceChainStatus {
        return store.verifyEvidenceChain(normalizeTenant(tenantId), limit)
    }

    private fun appendEvidence(
        tenantId: String,
        actionType: String,
        actorId: String,
        resourceType: String,
        resourceId: String,
        payloadJson: String,
    ) {
        store.appendEvidenceEvent(
            RegenEvidenceEvent(
                tenantId = tenantId,
                actionType = actionType,
                actorId = actorId,
                resourceType = resourceType,
                resourceId = resourceId,
                payloadJson = payloadJson,
                createdAtMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun ensureGatewayExists(
        tenantId: String,
        gatewayId: String,
    ) {
        val gateway = store.getGateway(normalizeTenant(tenantId), gatewayId.trim())
        if (gateway == null) {
            throw BadRequestException(code = "gateway_missing", message = "Gateway is not registered")
        }
    }

    private fun requireContent(contentJson: String): String {
        val trimmed = contentJson.trim()
        if (trimmed.isBlank()) {
            throw BadRequestException(code = "protocol_content_required", message = "contentJson is required")
        }
        return trimmed
    }

    private fun normalizeTenant(tenantId: String): String {
        val value = tenantId.trim()
        if (value.isBlank()) {
            throw BadRequestException(code = "tenant_required", message = "tenantId is required")
        }
        return value
    }

    private fun normalizeProtocol(protocolId: String): String {
        val value = protocolId.trim()
        if (value.isBlank()) {
            throw BadRequestException(code = "protocol_id_required", message = "protocolId is required")
        }
        return value
    }

    private fun normalizeActor(actorId: String): String {
        val value = actorId.trim()
        if (value.isBlank()) {
            throw BadRequestException(code = "actor_id_required", message = "actorId is required")
        }
        return value
    }

    private fun sanitizeLimit(
        raw: Int,
        fallback: Int,
    ): Int {
        return if (raw <= 0) fallback else raw.coerceAtMost(10_000)
    }
}

data class RegenVersionDiff(
    val summary: String,
    val addedLines: List<String>,
    val removedLines: List<String>,
)

data class IngestResult(
    val accepted: Int,
    val rejected: Int,
)

data class RegenGatewayConfig(
    val tenantId: String = "",
    val gatewayId: String = "",
    val activeProtocolId: String = "",
    val activeProtocolVersion: Int = 0,
    val protocolContentJson: String = "",
    val issuedAtMs: Long = 0,
    val empty: Boolean = false,
)
