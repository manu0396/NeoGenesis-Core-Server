package com.neogenesis.gateway

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

data class GatewayConfig(
    val gatewayId: String,
    val tenantId: String,
    val serverHost: String,
    val serverPort: Int,
    val useTls: Boolean,
    val mtlsEnabled: Boolean,
    val clientCertPath: String?,
    val clientKeyPath: String?,
    val serverCaPath: String?,
    val connectTimeoutMs: Long,
    val requestTimeoutMs: Long,
    val heartbeatIntervalMs: Long,
    val queuePath: String,
    val queueMaxBytes: Long,
    val telemetryBatchSize: Int,
    val eventsBatchSize: Int,
    val telemetryMaxRatePerSecond: Int,
    val driverName: String,
) {
    companion object {
        fun fromEnv(): GatewayConfig {
            return GatewayConfig(
                gatewayId = envRequired("GATEWAY_ID"),
                tenantId = envRequired("TENANT_ID"),
                serverHost = envOrDefault("SERVER_HOST", "localhost"),
                serverPort = envOrDefault("SERVER_PORT", "50051").toInt(),
                useTls = envOrDefault("SERVER_TLS", "false").toBooleanStrictOrNull() ?: false,
                mtlsEnabled = envOrDefault("GATEWAY_MTLS", "false").toBooleanStrictOrNull() ?: false,
                clientCertPath = envOptional("GATEWAY_CLIENT_CERT"),
                clientKeyPath = envOptional("GATEWAY_CLIENT_KEY"),
                serverCaPath = envOptional("SERVER_CA_CERT"),
                connectTimeoutMs = envOrDefault("CONNECT_TIMEOUT_MS", "5000").toLong(),
                requestTimeoutMs = envOrDefault("REQUEST_TIMEOUT_MS", "10000").toLong(),
                heartbeatIntervalMs = envOrDefault("HEARTBEAT_INTERVAL_MS", "15000").toLong(),
                queuePath = envOrDefault("QUEUE_PATH", "gateway-data/queue"),
                queueMaxBytes = envOrDefault("QUEUE_MAX_BYTES", "104857600").toLong(),
                telemetryBatchSize = envOrDefault("TELEMETRY_BATCH_SIZE", "200").toInt(),
                eventsBatchSize = envOrDefault("EVENTS_BATCH_SIZE", "200").toInt(),
                telemetryMaxRatePerSecond = envOrDefault("TELEMETRY_MAX_RPS", "2000").toInt(),
                driverName = envOrDefault("DRIVER", "simulated"),
            )
        }

        fun load(path: Path): GatewayConfig {
            val props = Properties()
            Files.newInputStream(path).use(props::load)
            return fromProperties(props)
        }

        private fun fromProperties(props: Properties): GatewayConfig {
            return GatewayConfig(
                gatewayId = props.getRequired("gateway.id"),
                tenantId = props.getRequired("tenant.id"),
                serverHost = props.getProperty("server.host", "localhost"),
                serverPort = props.getProperty("server.port", "50051").toInt(),
                useTls = props.getProperty("server.tls", "false").toBooleanStrictOrNull() ?: false,
                mtlsEnabled = props.getProperty("gateway.mtls", "false").toBooleanStrictOrNull() ?: false,
                clientCertPath = props.getProperty("gateway.clientCert"),
                clientKeyPath = props.getProperty("gateway.clientKey"),
                serverCaPath = props.getProperty("server.caCert"),
                connectTimeoutMs = props.getProperty("connect.timeout.ms", "5000").toLong(),
                requestTimeoutMs = props.getProperty("request.timeout.ms", "10000").toLong(),
                heartbeatIntervalMs = props.getProperty("heartbeat.interval.ms", "15000").toLong(),
                queuePath = props.getProperty("queue.path", "gateway-data/queue"),
                queueMaxBytes = props.getProperty("queue.max.bytes", "104857600").toLong(),
                telemetryBatchSize = props.getProperty("telemetry.batch.size", "200").toInt(),
                eventsBatchSize = props.getProperty("events.batch.size", "200").toInt(),
                telemetryMaxRatePerSecond = props.getProperty("telemetry.max.rps", "2000").toInt(),
                driverName = props.getProperty("driver", "simulated"),
            )
        }

        private fun envRequired(key: String): String {
            return envOptional(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing required env: $key")
        }

        private fun envOptional(key: String): String? = System.getenv(key)

        private fun envOrDefault(key: String, defaultValue: String): String = System.getenv(key) ?: defaultValue

        private fun Properties.getRequired(key: String): String {
            return getProperty(key)?.takeIf { it.isNotBlank() }
                ?: error("Missing required property: $key")
        }
    }
}
