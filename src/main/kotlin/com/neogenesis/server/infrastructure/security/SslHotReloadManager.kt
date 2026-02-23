package com.neogenesis.server.infrastructure.security

import io.grpc.util.AdvancedTlsX509KeyManager
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SslHotReloadManager(
    private val certPath: String?,
    private val keyPath: String?,
    private val reloadIntervalMinutes: Long,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(SslHotReloadManager::class.java)
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var reloadTask: AdvancedTlsX509KeyManager.Closeable? = null

    val keyManager = AdvancedTlsX509KeyManager()

    fun start() {
        if (certPath.isNullOrBlank() || keyPath.isNullOrBlank()) {
            logger.warn("SSL hot reload disabled: cert/key path not configured")
            return
        }

        val certFile = File(certPath)
        val keyFile = File(keyPath)
        if (!certFile.exists() || !keyFile.exists()) {
            logger.error("SSL hot reload disabled: missing files cert=$certPath key=$keyPath")
            return
        }

        runCatching {
            keyManager.updateIdentityCredentialsFromFile(certFile, keyFile)
            reloadTask =
                keyManager.updateIdentityCredentialsFromFile(
                    certFile,
                    keyFile,
                    reloadIntervalMinutes.coerceAtLeast(1L),
                    TimeUnit.MINUTES,
                    scheduler,
                )
            logger.info("gRPC mTLS certificate hot reload enabled")
        }.onFailure { error ->
            logger.error("Failed to initialize SSL hot reload", error)
        }
    }

    override fun close() {
        runCatching { reloadTask?.close() }
        scheduler.shutdownNow()
    }
}
