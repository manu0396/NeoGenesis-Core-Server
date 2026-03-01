package com.neogenesis.gateway

import java.security.MessageDigest
import java.util.UUID

object Idempotency {
    fun newKey(): String = UUID.randomUUID().toString()

    fun hash(payload: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
