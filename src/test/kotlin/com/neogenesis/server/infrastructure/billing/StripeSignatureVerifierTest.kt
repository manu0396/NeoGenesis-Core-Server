package com.neogenesis.server.infrastructure.billing

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StripeSignatureVerifierTest {
    @Test
    fun verifies_valid_signature() {
        val secret = "whsec_test_secret"
        val payload = """{"id":"evt_123","type":"checkout.session.completed","data":{"object":{}}}"""
        val timestamp = System.currentTimeMillis() / 1000
        val signature = computeSignature(secret, "$timestamp.$payload")
        val header = "t=$timestamp,v1=$signature"

        val verifier = StripeSignatureVerifier(secret, toleranceSeconds = 300)
        assertTrue(verifier.verify(header, payload))
    }

    @Test
    fun rejects_invalid_signature() {
        val secret = "whsec_test_secret"
        val payload = """{"id":"evt_123","type":"checkout.session.completed","data":{"object":{}}}"""
        val timestamp = System.currentTimeMillis() / 1000
        val header = "t=$timestamp,v1=deadbeef"

        val verifier = StripeSignatureVerifier(secret, toleranceSeconds = 300)
        assertFalse(verifier.verify(header, payload))
    }

    private fun computeSignature(
        secret: String,
        signedPayload: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(signedPayload.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
