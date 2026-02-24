package com.neogenesis.gateway

import java.io.File
import kotlinx.coroutines.delay

class CertificateWatcher(
    private val config: GatewayConfig,
) {
    private var lastFingerprint: String? = null
    private var lastSerial: String? = null

    fun currentSerial(): String = lastSerial.orEmpty()

    suspend fun watch() {
        while (true) {
            if (!config.mtlsEnabled) {
                delay(10_000)
                continue
            }
            val certPath = config.clientCertPath
            if (certPath.isNullOrBlank()) {
                delay(10_000)
                continue
            }
            val file = File(certPath)
            if (!file.exists()) {
                delay(10_000)
                continue
            }
            val fingerprint = Idempotency.hash(file.readText())
            lastSerial = fingerprint.take(16)
            if (lastFingerprint != null && lastFingerprint != fingerprint) {
                println("certificate rotation detected; restart required")
            }
            lastFingerprint = fingerprint
            delay(10_000)
        }
    }
}
