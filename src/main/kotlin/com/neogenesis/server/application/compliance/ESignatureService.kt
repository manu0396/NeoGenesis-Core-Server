package com.neogenesis.server.application.compliance

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.model.AuditEvent
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class ESignature(
    val id: String,
    val tenantId: String,
    val actorId: String,
    val resourceType: String,
    val resourceId: String,
    val checksumSha256: String,
    val justification: String,
    val signedAt: Instant,
    val signatureHash: String,
)

class ESignatureService(
    private val auditTrailService: AuditTrailService,
    private val signingSecret: String = "compliance-signing-secret-placeholder",
) {
    fun sign(
        tenantId: String,
        actorId: String,
        resourceType: String,
        resourceId: String,
        content: String,
        justification: String,
    ): ESignature {
        val contentHash = sha256(content.toByteArray(Charsets.UTF_8))
        val signedAt = Instant.now()
        val signatureHash = computeSignature(actorId, resourceType, resourceId, contentHash, signedAt)

        val signature =
            ESignature(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                actorId = actorId,
                resourceType = resourceType,
                resourceId = resourceId,
                checksumSha256 = contentHash,
                justification = justification,
                signedAt = signedAt,
                signatureHash = signatureHash,
            )

        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actorId,
                action = "compliance.esign.apply",
                resourceType = resourceType,
                resourceId = resourceId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-011", "REQ-FDA-PART11"),
                details =
                    mapOf(
                        "justification" to justification,
                        "signatureId" to signature.id,
                    ),
            ),
        )

        return signature
    }

    private fun computeSignature(
        actor: String,
        type: String,
        id: String,
        hash: String,
        time: Instant,
    ): String {
        val raw = "$actor|$type|$id|$hash|${time.toEpochMilli()}|$signingSecret"
        return sha256(raw.toByteArray(Charsets.UTF_8))
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
