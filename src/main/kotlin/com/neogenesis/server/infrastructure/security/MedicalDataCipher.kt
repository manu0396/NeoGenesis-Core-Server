package com.neogenesis.server.infrastructure.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MedicalDataCipher(base64Key: String) {
    private val key = SecretKeySpec(decodeKey(base64Key), "AES")

    fun encrypt(data: ByteArray): ByteArray {
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > 12) { "Encrypted payload is too short" }

        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        fun generateBase64Key(): String {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            return Base64.getEncoder().encodeToString(keyGenerator.generateKey().encoded)
        }

        private fun decodeKey(base64Key: String): ByteArray {
            val decoded = Base64.getDecoder().decode(base64Key)
            require(decoded.size == 32) {
                "Expected a 256-bit AES key encoded as Base64"
            }
            return decoded
        }
    }
}
