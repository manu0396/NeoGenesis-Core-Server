package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.resilience.IntegrationResilienceExecutor
import com.neogenesis.server.infrastructure.clinical.Hl7MllpClient
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class Hl7MllpGatewayService(
    private val mllpClient: Hl7MllpClient,
    private val clinicalIntegrationService: ClinicalIntegrationService,
    private val mllpConfig: AppConfig.ClinicalConfig.Hl7MllpConfig,
    private val metricsService: OperationalMetricsService,
    private val resilienceExecutor: IntegrationResilienceExecutor,
) {
    fun send(
        message: String,
        host: String,
        port: Int,
        actor: String,
    ): String {
        return resilienceExecutor.execute("hl7-mllp", "send") {
            runCatching {
                val ack =
                    mllpClient.send(
                        host = host,
                        port = port,
                        message = message,
                        connectTimeoutMs = mllpConfig.connectTimeoutMs,
                        readTimeoutMs = mllpConfig.readTimeoutMs,
                    )
                clinicalIntegrationService.ingestHl7(message, actor)
                metricsService.recordMllpOutbound("success")
                ack
            }.getOrElse {
                metricsService.recordMllpOutbound("error")
                throw it
            }
        }
    }

    fun onInboundMessage(message: String): String {
        return try {
            clinicalIntegrationService.ingestHl7(message, "mllp-listener")
            metricsService.recordMllpInbound()
            buildAck(message, "AA", "OK")
        } catch (error: Exception) {
            buildAck(message, "AE", error.message ?: "processing_error")
        }
    }

    private fun buildAck(
        message: String,
        code: String,
        text: String,
    ): String {
        val msh =
            message.split('\r', '\n')
                .firstOrNull { it.startsWith("MSH|") }
                ?.split('|')
                .orEmpty()

        val sendingApp = msh.getOrNull(2) ?: "UnknownSender"
        val sendingFacility = msh.getOrNull(3) ?: "UnknownFacility"
        val controlId = msh.getOrNull(9) ?: "UNKNOWN"
        val version = msh.getOrNull(11) ?: "2.5"
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())

        return "MSH|^~\\&|NeoGenesisCore|NeoGenesis|$sendingApp|$sendingFacility|$timestamp||ACK|${UUID.randomUUID()}|P|$version\r" +
            "MSA|$code|$controlId|$text\r"
    }
}
