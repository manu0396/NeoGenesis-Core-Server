package com.neogenesis.server.modules.evidence

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.regenops.RegenOpsStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.infrastructure.security.enforceRole
import com.neogenesis.server.infrastructure.security.tenantId
import com.neogenesis.server.modules.ApiException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Route.evidencePackModule(
    jobRepository: JobRepository,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    serverVersion: String,
    auditTrailService: AuditTrailService,
    regenOpsStore: RegenOpsStore,
    eventChainEnabled: Boolean,
) {
    authenticate("auth-jwt") {
        get("/evidence-pack/job/{jobId}/report.csv") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val correlationId = call.requireCorrelationId()
            val tenantId = call.requireTenantId()
            val actorId = call.actor()
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(tenantId, jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (!job.tenantId.isNullOrBlank() && job.tenantId != tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            auditTrailService.record(
                AuditEvent(
                    tenantId = tenantId,
                    actor = call.actor(),
                    action = "evidence.pack.report.export",
                    resourceType = "evidence_pack",
                    resourceId = jobId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-001"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )

            val report =
                EvidenceRunReport.from(
                    jobId = jobId,
                    tenantId = tenantId,
                    actorId = actorId,
                    correlationId = correlationId,
                    telemetryRepository = telemetryRepository,
                    twinMetricsRepository = twinMetricsRepository,
                    auditLogRepository = auditLogRepository,
                    serverVersion = serverVersion,
                )
            call.respondText(report.toCsv(), ContentType.Text.CSV)
        }

        get("/evidence-pack/job/{jobId}/report.pdf") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val correlationId = call.requireCorrelationId()
            val tenantId = call.requireTenantId()
            val actorId = call.actor()
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(tenantId, jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (!job.tenantId.isNullOrBlank() && job.tenantId != tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            auditTrailService.record(
                AuditEvent(
                    tenantId = tenantId,
                    actor = call.actor(),
                    action = "evidence.pack.report.export.pdf",
                    resourceType = "evidence_pack",
                    resourceId = jobId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-001"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )

            val report =
                EvidenceRunReport.from(
                    jobId = jobId,
                    tenantId = tenantId,
                    actorId = actorId,
                    correlationId = correlationId,
                    telemetryRepository = telemetryRepository,
                    twinMetricsRepository = twinMetricsRepository,
                    auditLogRepository = auditLogRepository,
                    serverVersion = serverVersion,
                )
            val pdfBytes = report.toPdfBytes()
            call.respondBytes(pdfBytes, ContentType.Application.Pdf)
        }

        get("/evidence-pack/job/{jobId}/bundle.zip") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val correlationId = call.requireCorrelationId()
            val tenantId = call.requireTenantId()
            val actorId = call.actor()
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(tenantId, jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            if (!job.tenantId.isNullOrBlank() && job.tenantId != tenantId) {
                throw ApiException("tenant_mismatch", "tenant mismatch", HttpStatusCode.Forbidden)
            }

            auditTrailService.record(
                AuditEvent(
                    tenantId = tenantId,
                    actor = call.actor(),
                    action = "evidence.pack.bundle.export",
                    resourceType = "evidence_pack",
                    resourceId = jobId,
                    outcome = "success",
                    requirementIds = listOf("REQ-ISO-001"),
                    details =
                        mapOf(
                            "tenantId" to tenantId,
                            "correlationId" to correlationId,
                        ),
                ),
            )

            val report =
                EvidenceRunReport.from(
                    jobId = jobId,
                    tenantId = tenantId,
                    actorId = actorId,
                    correlationId = correlationId,
                    telemetryRepository = telemetryRepository,
                    twinMetricsRepository = twinMetricsRepository,
                    auditLogRepository = auditLogRepository,
                    serverVersion = serverVersion,
                )
            val bundleBytes =
                buildEvidenceBundle(
                    report = report,
                    jobId = jobId,
                    tenantId = tenantId,
                    regenOpsStore = regenOpsStore,
                    includeEventChain = eventChainEnabled,
                )
            call.respondBytes(bundleBytes, ContentType.Application.Zip)
        }
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

private fun EvidenceRunReport.toPdfBytes(): ByteArray {
    val lines =
        listOf(
            "Evidence Pack Report",
            "job_id: $jobId",
            "tenant_id: $tenantId",
            "actor_id: $actorId",
            "correlation_id: $correlationId",
            "generated_at: $generatedAt",
            "server_version: $serverVersion",
            "telemetry_count: $telemetryCount",
            "twin_count: $twinCount",
            "audit_event_count: $auditEventCount",
            "last_telemetry_at: ${lastTelemetryAt.orEmpty()}",
            "last_twin_at: ${lastTwinAt.orEmpty()}",
        )
    return SimplePdfBuilder.render(lines)
}

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

private fun buildEvidenceBundle(
    report: EvidenceRunReport,
    jobId: String,
    tenantId: String,
    regenOpsStore: RegenOpsStore,
    includeEventChain: Boolean,
): ByteArray {
    val prefix = sanitizeJobId(jobId)
    val files = linkedMapOf<String, ByteArray>()
    files["$prefix-report.csv"] = report.toCsv().toByteArray(Charsets.UTF_8)
    files["$prefix-report.pdf"] = report.toPdfBytes()

    if (includeEventChain) {
        val runEvents = regenOpsStore.listRunEvents(tenantId, jobId, 0, 0, 10_000)
        val chainEntries = mutableListOf<EventChainEntry>()
        var prevHash: String? = null
        runEvents.forEach { event ->
            val payloadHash = sha256(event.payloadJson.toByteArray(Charsets.UTF_8))
            val eventHash =
                sha256(
                    buildString {
                        append(prevHash.orEmpty())
                        append('|')
                        append(payloadHash)
                        append('|')
                        append(event.eventType)
                        append('|')
                        append(event.createdAtMs)
                    }.toByteArray(Charsets.UTF_8),
                )
            chainEntries +=
                EventChainEntry(
                    seq = event.seq,
                    eventType = event.eventType,
                    source = event.source,
                    createdAtMs = event.createdAtMs,
                    payloadHash = payloadHash,
                    prevHash = prevHash,
                    eventHash = eventHash,
                )
            prevHash = eventHash
        }
        val chain =
            EventChainReport(
                jobId = jobId,
                tenantId = tenantId,
                generatedAt = Instant.now().toString(),
                entries = chainEntries,
            )
        files["event-chain.json"] = Json.encodeToString(chain).toByteArray(Charsets.UTF_8)
    }

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
    val manifestJson = Json.encodeToString(manifest)
    files["manifest.json"] = manifestJson.toByteArray(Charsets.UTF_8)

    // Sign the manifest
    val signature = hmacSha256(manifestJson.toByteArray(Charsets.UTF_8), "evidence-signing-key-placeholder".toByteArray(Charsets.UTF_8))
    files["manifest.sig"] = signature.toByteArray(Charsets.UTF_8)

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        // Sort files for determinism
        files.keys.sorted().forEach { name ->
            val bytes = files[name]!!
            val entry = ZipEntry(name)
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun hmacSha256(
    data: ByteArray,
    key: ByteArray,
): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    val secretKey = javax.crypto.spec.SecretKeySpec(key, "HmacSHA256")
    mac.init(secretKey)
    return mac.doFinal(data).joinToString("") { "%02x".format(it) }
}

private fun sanitizeJobId(jobId: String): String {
    val filtered = jobId.replace(Regex("[^A-Za-z0-9_-]+"), "_")
    return filtered.ifBlank { "job" }
}

@Serializable
private data class EventChainEntry(
    val seq: Long,
    val eventType: String,
    val source: String,
    val createdAtMs: Long,
    val payloadHash: String,
    val prevHash: String?,
    val eventHash: String,
)

@Serializable
private data class EventChainReport(
    val jobId: String,
    val tenantId: String,
    val generatedAt: String,
    val entries: List<EventChainEntry>,
)

private fun sha256(bytes: ByteArray): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}

private object SimplePdfBuilder {
    fun render(lines: List<String>): ByteArray {
        val content = buildContent(lines)
        val contentBytes = content.toByteArray(Charsets.US_ASCII)
        val objects = mutableListOf<String>()
        objects +=
            buildPdfObject(
                "1 0 obj",
                "<< /Type /Catalog /Pages 2 0 R >>",
                "endobj",
            )
        objects +=
            buildPdfObject(
                "2 0 obj",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "endobj",
            )
        objects +=
            buildPdfObject(
                "3 0 obj",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "endobj",
            )
        objects +=
            buildPdfObject(
                "4 0 obj",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "endobj",
            )
        objects +=
            buildPdfObject(
                "5 0 obj",
                "<< /Length ${contentBytes.size} >>",
                "stream",
                content,
                "endstream",
                "endobj",
            )

        val header = "%PDF-1.4\n"
        val offsets = mutableListOf<Int>()
        var cursor = header.toByteArray(Charsets.US_ASCII).size
        objects.forEach { obj ->
            offsets += cursor
            cursor += obj.toByteArray(Charsets.US_ASCII).size
        }
        val xrefStart = cursor
        val xref =
            buildString {
                append("xref\n0 ${objects.size + 1}\n")
                append("0000000000 65535 f \n")
                offsets.forEach { offset ->
                    append(String.format("%010d 00000 n \n", offset))
                }
            }
        val trailer =
            "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n"

        val output = StringBuilder()
        output.append(header)
        objects.forEach { output.append(it) }
        output.append(xref)
        output.append(trailer)
        return output.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun buildPdfObject(vararg lines: String): String {
        return buildString {
            lines.forEach { appendLine(it) }
        }
    }

    private fun buildContent(lines: List<String>): String {
        val escaped = lines.map { escapePdf(it) }
        val builder = StringBuilder()
        builder.append("BT\n/F1 12 Tf\n50 750 Td\n")
        escaped.forEachIndexed { index, line ->
            if (index > 0) {
                builder.append("0 -16 Td\n")
            }
            builder.append("($line) Tj\n")
        }
        builder.append("ET\n")
        return builder.toString()
    }

    private fun escapePdf(value: String): String {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    }
}
