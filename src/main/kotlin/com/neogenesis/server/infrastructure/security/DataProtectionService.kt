package com.neogenesis.server.infrastructure.security

import java.util.Base64

data class ProtectedPayload(
    val content: String,
    val keyId: String?
)

class DataProtectionService(
    private val enabled: Boolean,
    phiKeyBase64: String?,
    piiKeyBase64: String?
) {
    private val phiCipher = phiKeyBase64?.let { MedicalDataCipher(it) }
    private val piiCipher = piiKeyBase64?.let { MedicalDataCipher(it) }

    fun protect(content: String, classification: String): ProtectedPayload {
        if (!enabled) {
            return ProtectedPayload(content = content, keyId = null)
        }
        val (keyId, cipher) = when (classification.uppercase()) {
            "PII" -> "PII" to piiCipher
            else -> "PHI" to phiCipher
        }
        requireNotNull(cipher) { "No encryption key configured for classification=$classification" }
        val encrypted = cipher.encrypt(content.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(encrypted)
        return ProtectedPayload(content = "enc:v1:$keyId:$encoded", keyId = keyId)
    }

    fun unprotect(content: String): String {
        if (!content.startsWith("enc:v1:")) {
            return content
        }
        val parts = content.split(':', limit = 4)
        if (parts.size != 4) {
            return content
        }
        val keyId = parts[2]
        val payloadB64 = parts[3]
        val cipher = when (keyId.uppercase()) {
            "PII" -> piiCipher
            else -> phiCipher
        } ?: return content
        val encrypted = runCatching { Base64.getDecoder().decode(payloadB64) }.getOrElse { return content }
        val plain = runCatching { cipher.decrypt(encrypted) }.getOrElse { return content }
        return plain.toString(Charsets.UTF_8)
    }
}
