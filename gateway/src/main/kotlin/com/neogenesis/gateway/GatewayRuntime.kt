package com.neogenesis.gateway

import com.neogenesis.gateway.driver.DriverRegistry
import com.neogenesis.gateway.queue.FileBackedQueue
import com.neogenesis.grpc.GatewayRunEvent
import com.neogenesis.grpc.GatewayTelemetry
import io.grpc.ClientInterceptors
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GatewayRuntime(
    private val config: GatewayConfig,
) {
    private val running = AtomicBoolean(false)
    private val startedAt = Instant.now()

    fun startBlocking() {
        if (!running.compareAndSet(false, true)) return
        println("Gateway starting: gatewayId=${config.gatewayId}, tenantId=${config.tenantId}")
        println("Server: ${config.serverHost}:${config.serverPort} tls=${config.useTls} mtls=${config.mtlsEnabled}")

        Runtime.getRuntime().addShutdownHook(Thread { stop() })

        HealthCommand(config, startedAt).startAsync()

        val channel =
            ClientInterceptors.intercept(
                GrpcClientFactory.build(config),
                MetadataInterceptor(config),
            )
        val client = GatewayClient(channel, config)
        val queue = FileBackedQueue(Paths.get(config.queuePath), config.queueMaxBytes)
        val limiter = BackpressureLimiter(config.telemetryMaxRatePerSecond)
        val queueProcessor = QueueProcessor(client, queue, config, limiter)
        val certWatcher = CertificateWatcher(config)

        val scope = CoroutineScope(Dispatchers.Default)
        val driver = DriverRegistry().create(config.driverName)
        scope.launch { registerGateway(client, config, certWatcher) }
        scope.launch { heartbeatLoop(client, config) }
        scope.launch {
            driver.telemetryStream().collect { point ->
                val telemetry =
                    GatewayTelemetry.newBuilder()
                        .setRunId(point.runId)
                        .setMetricKey(point.metricKey)
                        .setMetricValue(point.metricValue)
                        .setUnit(point.unit)
                        .setDriftScore(point.driftScore)
                        .setRecordedAtMs(point.recordedAtMs)
                        .setCorrelationId(Idempotency.newKey())
                        .build()
                queueProcessor.enqueueTelemetry(telemetry)
            }
        }
        scope.launch {
            driver.eventStream().collect { event ->
                val runEvent =
                    GatewayRunEvent.newBuilder()
                        .setRunId(event.runId)
                        .setEventType(event.eventType)
                        .setPayloadJson(event.payloadJson)
                        .setCreatedAtMs(event.createdAtMs)
                        .setCorrelationId(Idempotency.newKey())
                        .build()
                queueProcessor.enqueueEvent(runEvent)
            }
        }
        scope.launch { queueProcessor.runBlockingLoop() }
        scope.launch { certWatcher.watch() }

        runBlocking {
            while (running.get()) {
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        val uptime = Duration.between(startedAt, Instant.now()).seconds
        println("Gateway stopping. Uptime=${uptime}s")
    }

    private suspend fun heartbeatLoop(
        client: GatewayClient,
        config: GatewayConfig,
    ) {
        while (running.get()) {
            try {
                val certSerial = config.clientCertPath ?: ""
                client.heartbeat(certSerial)
            } catch (error: Throwable) {
                println("heartbeat failed: ${error.message}")
            }
            kotlinx.coroutines.delay(config.heartbeatIntervalMs)
        }
    }

    private suspend fun registerGateway(
        client: GatewayClient,
        config: GatewayConfig,
        certWatcher: CertificateWatcher,
    ) {
        try {
            val certSerial = certWatcher.currentSerial()
            client.registerGateway(displayName = config.gatewayId, certificateSerial = certSerial)
            println("gateway registered")
        } catch (error: Throwable) {
            println("gateway registration failed: ${error.message}")
        }
    }
}
