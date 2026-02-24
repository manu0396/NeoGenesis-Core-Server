package com.neogenesis.server.modules

import com.neogenesis.server.application.billing.BillingService
import com.neogenesis.server.infrastructure.persistence.AuditChainStatus
import com.neogenesis.server.infrastructure.persistence.AuditLogRecord
import com.neogenesis.server.infrastructure.persistence.AuditLogRepository
import com.neogenesis.server.infrastructure.persistence.CanonicalRole
import com.neogenesis.server.infrastructure.persistence.JobRepository
import com.neogenesis.server.infrastructure.persistence.TelemetryRepository
import com.neogenesis.server.infrastructure.persistence.TwinMetricsRepository
import com.neogenesis.server.infrastructure.security.actor
import com.neogenesis.server.infrastructure.security.enforceRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

fun Route.auditModule(
    jobRepository: JobRepository,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    serverVersion: String,
    billingService: BillingService,
) {
    authenticate("auth-jwt") {
        get("/audit/job/{jobId}") {
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            val entries = auditLogRepository.listByJob(job.id, 10_000)
            val verification = auditLogRepository.verifyJobChain(job.id, 10_000)

            call.respond(
                HttpStatusCode.OK,
                AuditJobResponse(
                    jobId = job.id,
                    verification = verification.toResponse(),
                    entries = entries.map { it.toResponse() },
                ),
            )
        }

        get("/evidence/job/{jobId}") {
            billingService.requireEntitlement(call.actor(), "audit:evidence_export")
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)

            val evidence = buildEvidencePackage(job, telemetryRepository, twinMetricsRepository, auditLogRepository, serverVersion)
            call.respond(HttpStatusCode.OK, evidence)
        }

        get("/exports/job/{jobId}.json") {
            billingService.requireEntitlement(call.actor(), "audit:evidence_export")
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            val evidence = buildEvidencePackage(job, telemetryRepository, twinMetricsRepository, auditLogRepository, serverVersion)
            call.respond(HttpStatusCode.OK, evidence)
        }

        get("/exports/job/{jobId}.csv") {
            billingService.requireEntitlement(call.actor(), "audit:evidence_export")
            call.enforceRole(CanonicalRole.ADMIN, CanonicalRole.AUDITOR)
            val jobId = call.parameters["jobId"]?.trim().orEmpty()
            if (jobId.isBlank()) {
                throw ApiException("invalid_request", "jobId is required", HttpStatusCode.BadRequest)
            }
            val job =
                jobRepository.get(jobId)
                    ?: throw ApiException("job_not_found", "Job not found", HttpStatusCode.NotFound)
            val evidence = buildEvidencePackage(job, telemetryRepository, twinMetricsRepository, auditLogRepository, serverVersion)
            call.respondText(evidence.toCsv())
        }
    }
}

@Serializable
data class AuditJobResponse(
    val jobId: String,
    val verification: AuditChainResponse,
    val entries: List<AuditEntryResponse>,
)

@Serializable
data class AuditChainResponse(
    val valid: Boolean,
    val checked: Int,
    val failureIndex: Int?,
    val reason: String?,
)

@Serializable
data class AuditEntryResponse(
    val id: Long,
    val jobId: String?,
    val actorId: String,
    val eventType: String,
    val deviceId: String?,
    val payloadJson: String,
    val payloadHash: String,
    val prevHash: String?,
    val eventHash: String,
    val createdAt: String,
)

@Serializable
data class EvidencePackageV1(
    val packageVersion: String,
    val generatedAt: String,
    val serverVersion: String,
    val metadata: EvidenceMetadata,
    val telemetry: List<EvidenceRecord>,
    val digitalTwin: List<EvidenceRecord>,
    val audit: List<AuditEntryResponse>,
    val auditChain: AuditChainResponse,
    val bundleHash: String,
    val bundleSignature: String? = null,
    val signatureAlgorithm: String? = null,
)

@Serializable
data class EvidenceMetadata(
    val jobId: String,
    val deviceId: String,
    val tenantId: String?,
    val status: String,
)

@Serializable
data class EvidenceRecord(
    val id: String,
    val timestamp: String,
    val payloadJson: String,
)

private fun AuditChainStatus.toResponse(): AuditChainResponse {
    return AuditChainResponse(
        valid = valid,
        checked = checked,
        failureIndex = failureIndex,
        reason = reason,
    )
}

private fun AuditLogRecord.toResponse(): AuditEntryResponse {
    return AuditEntryResponse(
        id = id,
        jobId = jobId,
        actorId = actorId,
        eventType = eventType,
        deviceId = deviceId,
        payloadJson = payloadJson,
        payloadHash = payloadHash,
        prevHash = prevHash,
        eventHash = eventHash,
        createdAt = createdAt.toString(),
    )
}

private fun sha256(value: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

private fun buildEvidencePackage(
    job: com.neogenesis.server.infrastructure.persistence.JobRecord,
    telemetryRepository: TelemetryRepository,
    twinMetricsRepository: TwinMetricsRepository,
    auditLogRepository: AuditLogRepository,
    serverVersion: String,
): EvidencePackageV1 {
    val telemetry = telemetryRepository.listByJob(job.id, null, null, 5_000)
    val twin = twinMetricsRepository.listByJob(job.id, null, null, 5_000)
    val audit = auditLogRepository.listByJob(job.id, 10_000)
    val verification = auditLogRepository.verifyJobChain(job.id, 10_000)
    val evidenceHash =
        sha256(
            buildString {
                append(job.id)
                append('|')
                append(telemetry.joinToString("|") { it.id.toString() + it.payloadJson + it.recordedAt.toEpochMilli() })
                append('|')
                append(twin.joinToString("|") { it.id.toString() + it.payloadJson + it.recordedAt.toEpochMilli() })
                append('|')
                append(audit.joinToString("|") { it.id.toString() + it.eventHash })
                append('|')
                append(serverVersion)
            },
        )
    val signature = signBundleHash(evidenceHash)
    return EvidencePackageV1(
        packageVersion = "1",
        generatedAt = Instant.now().toString(),
        serverVersion = serverVersion,
        metadata =
            EvidenceMetadata(
                jobId = job.id,
                deviceId = job.deviceId,
                tenantId = job.tenantId,
                status = job.status,
            ),
        telemetry =
            telemetry.map {
                EvidenceRecord(
                    id = it.id.toString(),
                    timestamp = it.recordedAt.toString(),
                    payloadJson = it.payloadJson,
                )
            },
        digitalTwin =
            twin.map {
                EvidenceRecord(
                    id = it.id.toString(),
                    timestamp = it.recordedAt.toString(),
                    payloadJson = it.payloadJson,
                )
            },
        audit = audit.map { it.toResponse() },
        auditChain = verification.toResponse(),
        bundleHash = evidenceHash,
        bundleSignature = signature?.signature,
        signatureAlgorithm = signature?.algorithm,
    )
}

private data class BundleSignature(
    val signature: String,
    val algorithm: String,
)

private fun signBundleHash(bundleHash: String): BundleSignature? {
    val keyB64 = System.getenv("EVIDENCE_SIGNING_KEY_B64")?.trim().orEmpty()
    if (keyB64.isBlank()) {
        return null
    }
    return runCatching {
        val keyBytes = Base64.getDecoder().decode(keyB64)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("Ed25519")
        val privateKey = keyFactory.generatePrivate(spec)
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(bundleHash.toByteArray(Charsets.UTF_8))
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        BundleSignature(signature = signature, algorithm = "Ed25519")
    }.getOrNull()
}

private fun EvidencePackageV1.toCsv(): String {
    val lines = mutableListOf<String>()
    lines += "section,key,value"
    lines += "metadata,jobId,${metadata.jobId}"
    lines += "metadata,deviceId,${metadata.deviceId}"
    lines += "metadata,tenantId,${metadata.tenantId ?: ""}"
    lines += "metadata,status,${metadata.status}"
    lines += "bundle,hash,$bundleHash"
    lines += "bundle,signature,${bundleSignature ?: ""}"
    lines += "bundle,signatureAlgorithm,${signatureAlgorithm ?: ""}"
    audit.forEach { entry ->
        lines += "audit,${entry.id},${entry.eventType}"
    }
    telemetry.forEach { entry ->
        lines += "telemetry,${entry.id},${entry.timestamp}"
    }
    digitalTwin.forEach { entry ->
        lines += "twin,${entry.id},${entry.timestamp}"
    }
    return lines.joinToString("\n")
}
