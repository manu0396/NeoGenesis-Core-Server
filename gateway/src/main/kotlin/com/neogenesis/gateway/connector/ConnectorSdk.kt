package com.neogenesis.gateway.connector

import com.neogenesis.gateway.queue.OfflineQueue
import com.neogenesis.gateway.queue.QueueItem
import com.neogenesis.gateway.queue.QueueItemType
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

enum class Capability {
    Telemetry,
    Commands,
    Diagnostics,
}

data class ConnectorConfigField(
    val key: String,
    val description: String,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val secret: Boolean = false,
)

data class ConnectorManifest(
    val id: String,
    val name: String,
    val version: String,
    val vendor: String,
    val capabilities: Set<Capability>,
    val telemetrySchema: TelemetrySchema,
    val configSchema: List<ConnectorConfigField> = emptyList(),
    val description: String? = null,
    val minGatewayVersion: String? = null,
)

enum class DriverState {
    Created,
    Initialized,
    Started,
    Stopped,
    Failed,
}

data class DriverLifecycle(
    val state: DriverState,
    val updatedAt: Instant,
    val error: String? = null,
)

data class TelemetrySchema(
    val name: String,
    val version: String,
    val fields: List<TelemetryField>,
)

data class TelemetryField(
    val name: String,
    val type: String,
    val unit: String? = null,
)

data class DriverContext(
    val tenantId: String,
    val correlationId: String,
    val config: Map<String, String>,
)

data class TelemetryEvent(
    val timestamp: Instant,
    val payload: Map<String, String>,
)

data class DriverHealth(
    val status: String,
    val details: Map<String, String> = emptyMap(),
)

interface Driver {
    val id: String
    val capabilities: Set<Capability>
    val telemetrySchema: TelemetrySchema
    val manifest: ConnectorManifest
        get() =
            ConnectorManifest(
                id = id,
                name = id,
                version = "1.0.0",
                vendor = "unknown",
                capabilities = capabilities,
                telemetrySchema = telemetrySchema,
            )

    suspend fun init(context: DriverContext)

    suspend fun start()

    suspend fun stop()

    suspend fun health(): DriverHealth

    suspend fun readTelemetry(): TelemetryEvent
}

class ResilientDriver(
    private val delegate: Driver,
    private val offlineQueue: OfflineQueue,
    private val maxInFlight: Int = 100,
) : Driver by delegate {
    private val inFlight = AtomicInteger(0)

    override suspend fun readTelemetry(): TelemetryEvent {
        // Backpressure check
        while (inFlight.get() >= maxInFlight) {
            delay(10)
        }
        
        return try {
            inFlight.incrementAndGet()
            val event = delegate.readTelemetry()
            // If we have items in offline queue, we should probably drain them first or alongside
            event
        } catch (e: Exception) {
            // Buffer to offline queue if read fails (if driver supports buffering)
            throw e
        } finally {
            inFlight.decrementAndGet()
        }
    }
    
    suspend fun bufferEvent(event: TelemetryEvent) {
        val item = QueueItem(
            id = UUID.randomUUID().toString(),
            type = QueueItemType.TELEMETRY,
            createdAtMs = event.timestamp.toEpochMilli(),
            payload = event.payload.entries.joinToString(",") { "${it.key}=${it.value}" }
        )
        offlineQueue.append(item)
    }
}

class SandboxedDriver(
    private val delegate: Driver,
    private val timeouts: DriverTimeouts,
) : Driver {
    constructor(delegate: Driver, timeoutMs: Long) : this(delegate, DriverTimeouts.fromSingle(timeoutMs))

    override val id: String = delegate.id
    override val capabilities: Set<Capability> = delegate.capabilities
    override val telemetrySchema: TelemetrySchema = delegate.telemetrySchema
    override val manifest: ConnectorManifest = delegate.manifest

    override suspend fun init(context: DriverContext) {
        runWithTimeout("init", timeouts.initMs) { delegate.init(context) }
    }

    override suspend fun start() {
        runWithTimeout("start", timeouts.startMs) { delegate.start() }
    }

    override suspend fun stop() {
        runWithTimeout("stop", timeouts.stopMs) { delegate.stop() }
    }

    override suspend fun health(): DriverHealth =
        runWithTimeout("health", timeouts.healthMs) { delegate.health() }

    override suspend fun readTelemetry(): TelemetryEvent =
        runWithTimeout("readTelemetry", timeouts.readTelemetryMs) { delegate.readTelemetry() }

    private suspend fun <T> runWithTimeout(
        operation: String,
        timeoutMs: Long,
        block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (_: TimeoutCancellationException) {
            throw DriverTimeoutException(operation, timeoutMs)
        }
    }
}

data class DriverTimeouts(
    val initMs: Long,
    val startMs: Long,
    val stopMs: Long,
    val healthMs: Long,
    val readTelemetryMs: Long,
) {
    companion object {
        fun fromSingle(timeoutMs: Long): DriverTimeouts {
            return DriverTimeouts(
                initMs = timeoutMs,
                startMs = timeoutMs,
                stopMs = timeoutMs,
                healthMs = timeoutMs,
                readTelemetryMs = timeoutMs,
            )
        }
    }
}

class DriverTimeoutException(
    val operation: String,
    val timeoutMs: Long,
) : RuntimeException("Driver operation '$operation' exceeded ${timeoutMs}ms")
