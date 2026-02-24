package com.neogenesis.gateway.certification

import com.neogenesis.gateway.connector.Capability
import com.neogenesis.gateway.connector.Driver
import com.neogenesis.gateway.connector.DriverContext
import com.neogenesis.gateway.connector.DriverHealth
import com.neogenesis.gateway.connector.SandboxedDriver
import com.neogenesis.gateway.connector.TelemetryEvent
import com.neogenesis.gateway.connector.TelemetryField
import com.neogenesis.gateway.connector.TelemetrySchema
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong
import kotlin.random.Random

data class CertificationConfig(
    val events: Int,
    val dropRate: Double,
    val reconnectAt: Int,
    val reconnectDelayMs: Long,
    val timeoutMs: Long,
    val outputDir: String,
)

data class CertificationReport(
    val connectorId: String,
    val eventsExpected: Int,
    val eventsReceived: Int,
    val dropRate: Double,
    val meanLatencyMs: Double,
    val p95LatencyMs: Double,
    val reconnectLatencyMs: Long,
    val status: String,
    val generatedAt: Instant,
)

data class LatencyStats(
    val meanMs: Double,
    val p95Ms: Double,
)

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val report = runBlocking { runCertification(config) }
    writeReport(config.outputDir, report)
    println("Connector certification report written to ${config.outputDir}")
}

private fun parseArgs(args: Array<String>): CertificationConfig {
    fun argValue(prefix: String): String? = args.firstOrNull { it.startsWith(prefix) }?.substringAfter(prefix)
    val events = argValue("--events=")?.toIntOrNull() ?: 200
    val dropRate = argValue("--dropRate=")?.toDoubleOrNull() ?: 0.02
    val reconnectAt = argValue("--reconnectAt=")?.toIntOrNull() ?: (events / 2)
    val reconnectDelayMs = argValue("--reconnectDelayMs=")?.toLongOrNull() ?: 250L
    val timeoutMs = argValue("--timeoutMs=")?.toLongOrNull() ?: 1_000L
    val outputDir = argValue("--output=") ?: "build/reports/connector-certification"
    return CertificationConfig(
        events = events,
        dropRate = dropRate,
        reconnectAt = reconnectAt,
        reconnectDelayMs = reconnectDelayMs,
        timeoutMs = timeoutMs,
        outputDir = outputDir,
    )
}

private suspend fun runCertification(config: CertificationConfig): CertificationReport {
    val driver =
        SandboxedDriver(
            delegate = SimulatedDriver(dropRate = config.dropRate),
            timeoutMs = config.timeoutMs,
        )
    val context =
        DriverContext(
            tenantId = "certification-tenant",
            correlationId = UUID.randomUUID().toString(),
            config = mapOf("mode" to "certification"),
        )
    driver.init(context)
    driver.start()

    val latenciesMs = mutableListOf<Long>()
    var received = 0
    var reconnectLatencyMs = 0L
    for (index in 1..config.events) {
        if (index == config.reconnectAt) {
            driver.stop()
            delay(config.reconnectDelayMs)
            val reconnectStart = System.nanoTime()
            driver.start()
            reconnectLatencyMs = nanosToMs(System.nanoTime() - reconnectStart)
        }
        val start = System.nanoTime()
        val receivedEvent =
            runCatching { driver.readTelemetry() }
                .onSuccess { _ -> received += 1 }
                .isSuccess
        if (receivedEvent) {
            latenciesMs += nanosToMs(System.nanoTime() - start)
        }
    }

    val stats = computeLatencyStats(latenciesMs)
    val dropRate = (config.events - received).toDouble() / config.events.toDouble()

    val status =
        when {
            dropRate > 0.1 -> "fail"
            p95LatencyMs > 1_000 -> "warn"
            else -> "pass"
        }

    driver.stop()

    return CertificationReport(
        connectorId = driver.id,
        eventsExpected = config.events,
        eventsReceived = received,
        dropRate = dropRate,
        meanLatencyMs = stats.meanMs,
        p95LatencyMs = stats.p95Ms,
        reconnectLatencyMs = reconnectLatencyMs,
        status = status,
        generatedAt = Instant.now(),
    )
}

internal fun computeLatencyStats(values: List<Long>): LatencyStats {
    if (values.isEmpty()) {
        return LatencyStats(0.0, 0.0)
    }
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * 0.95).roundToLong().toInt()
    return LatencyStats(
        meanMs = values.average().coerceAtLeast(0.0),
        p95Ms = sorted[index].toDouble(),
    )
}

private fun nanosToMs(nanos: Long): Long = nanos / 1_000_000

private fun writeReport(outputDir: String, report: CertificationReport) {
    val dir = File(outputDir)
    if (!dir.exists()) {
        dir.mkdirs()
    }
    File(dir, "certification-report.json").writeText(report.toJson())
    File(dir, "certification-report.md").writeText(report.toMarkdown())
}

private fun CertificationReport.toJson(): String {
    return """
        {
          "connectorId": "$connectorId",
          "eventsExpected": $eventsExpected,
          "eventsReceived": $eventsReceived,
          "dropRate": $dropRate,
          "meanLatencyMs": $meanLatencyMs,
          "p95LatencyMs": $p95LatencyMs,
          "reconnectLatencyMs": $reconnectLatencyMs,
          "status": "$status",
          "generatedAt": "$generatedAt"
        }
    """.trimIndent()
}

private fun CertificationReport.toMarkdown(): String {
    return """
        |# Connector Certification Report
        |
        |- Connector: $connectorId
        |- Status: $status
        |- Events expected: $eventsExpected
        |- Events received: $eventsReceived
        |- Drop rate: ${"%.4f".format(dropRate)}
        |- Mean latency (ms): ${"%.2f".format(meanLatencyMs)}
        |- P95 latency (ms): ${"%.2f".format(p95LatencyMs)}
        |- Reconnect latency (ms): $reconnectLatencyMs
        |- Generated at: $generatedAt
        |
    """.trimMargin()
}

private class SimulatedDriver(
    private val dropRate: Double,
) : Driver {
    override val id: String = "simulated-driver"
    override val capabilities: Set<Capability> = setOf(Capability.Telemetry)
    override val telemetrySchema: TelemetrySchema =
        TelemetrySchema(
            name = "simulated",
            version = "1.0",
            fields = listOf(TelemetryField("value", "int", "unit")),
        )
    private val random = Random(4)
    private var started = false

    override suspend fun init(context: DriverContext) {
        started = false
    }

    override suspend fun start() {
        started = true
    }

    override suspend fun stop() {
        started = false
    }

    override suspend fun health(): DriverHealth {
        val status = if (started) "ok" else "stopped"
        return DriverHealth(status, mapOf("simulated" to "true"))
    }

    override suspend fun readTelemetry(): TelemetryEvent {
        if (!started) {
            error("driver_not_started")
        }
        if (random.nextDouble() < dropRate) {
            error("simulated_drop")
        }
        return TelemetryEvent(
            timestamp = Instant.now(),
            payload = mapOf("value" to random.nextInt(0, 100).toString()),
        )
    }
}
