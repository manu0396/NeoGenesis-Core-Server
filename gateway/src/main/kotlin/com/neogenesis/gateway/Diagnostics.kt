package com.neogenesis.gateway

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Diagnostics {
    fun exportBundle(config: GatewayConfig, outputPath: Path): Path {
        Files.createDirectories(outputPath.parent)
        ZipOutputStream(Files.newOutputStream(outputPath)).use { zip ->
            writeText(zip, "timestamp.txt", Instant.now().toString())
            writeText(zip, "config.txt", redactConfig(config))
            val logDir = File("logs")
            if (logDir.exists()) {
                logDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = "logs/${file.name}"
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return outputPath
    }

    private fun writeText(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun redactConfig(config: GatewayConfig): String {
        return buildString {
            appendLine("gatewayId=${config.gatewayId}")
            appendLine("tenantId=${config.tenantId}")
            appendLine("serverHost=${config.serverHost}")
            appendLine("serverPort=${config.serverPort}")
            appendLine("useTls=${config.useTls}")
            appendLine("mtlsEnabled=${config.mtlsEnabled}")
            appendLine("clientCertPath=${config.clientCertPath?.let { "***" }}")
            appendLine("clientKeyPath=${config.clientKeyPath?.let { "***" }}")
            appendLine("serverCaPath=${config.serverCaPath?.let { "***" }}")
            appendLine("connectTimeoutMs=${config.connectTimeoutMs}")
            appendLine("requestTimeoutMs=${config.requestTimeoutMs}")
            appendLine("heartbeatIntervalMs=${config.heartbeatIntervalMs}")
            appendLine("queuePath=${config.queuePath}")
            appendLine("queueMaxBytes=${config.queueMaxBytes}")
            appendLine("telemetryBatchSize=${config.telemetryBatchSize}")
            appendLine("eventsBatchSize=${config.eventsBatchSize}")
            appendLine("telemetryMaxRatePerSecond=${config.telemetryMaxRatePerSecond}")
            appendLine("driverName=${config.driverName}")
        }
    }
}
