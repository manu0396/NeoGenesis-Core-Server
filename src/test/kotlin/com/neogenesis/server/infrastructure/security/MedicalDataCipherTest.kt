package com.neogenesis.server.infrastructure.security

import kotlin.test.Test
import kotlin.test.assertContentEquals

class MedicalDataCipherTest {
    @Test
    fun `encrypt and decrypt round trip`() {
        val key = MedicalDataCipher.generateBase64Key()
        val cipher = MedicalDataCipher(key)
        val data = "neogenesis-payload".encodeToByteArray()

        val encrypted = cipher.encrypt(data)
        val decrypted = cipher.decrypt(encrypted)

        assertContentEquals(data, decrypted)
    }
}
