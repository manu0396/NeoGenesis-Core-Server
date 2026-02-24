package com.neogenesis.gateway

import com.neogenesis.grpc.GatewayAck
import com.neogenesis.grpc.GatewayConfig
import com.neogenesis.grpc.GatewayServiceGrpcKt
import com.neogenesis.grpc.GatewayTelemetry
import com.neogenesis.grpc.GatewayRunEvent
import com.neogenesis.grpc.FetchConfigRequest
import com.neogenesis.grpc.HeartbeatRequest
import com.neogenesis.grpc.PushRunEventsRequest
import com.neogenesis.grpc.PushTelemetryRequest
import com.neogenesis.grpc.RegisterGatewayRequest
import io.grpc.ManagedChannel
import kotlinx.coroutines.withTimeout

class GatewayClient(
    private val channel: ManagedChannel,
    private val config: GatewayConfig,
) {
    private val stub = GatewayServiceGrpcKt.GatewayServiceCoroutineStub(channel)

    suspend fun registerGateway(displayName: String, certificateSerial: String): com.neogenesis.grpc.GatewayRecord {
        val request =
            RegisterGatewayRequest.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .setDisplayName(displayName)
                .setCertificateSerial(certificateSerial)
                .setCorrelationId(Idempotency.newKey())
                .build()
        return withTimeout(config.requestTimeoutMs) { stub.registerGateway(request) }
    }

    suspend fun heartbeat(certificateSerial: String): com.neogenesis.grpc.GatewayRecord {
        val request =
            HeartbeatRequest.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .setCertificateSerial(certificateSerial)
                .setCorrelationId(Idempotency.newKey())
                .build()
        return withTimeout(config.requestTimeoutMs) { stub.heartbeat(request) }
    }

    suspend fun pushTelemetry(points: List<GatewayTelemetry>): GatewayAck {
        val request =
            PushTelemetryRequest.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .addAllTelemetry(points)
                .setCorrelationId(Idempotency.newKey())
                .build()
        return withTimeout(config.requestTimeoutMs) { stub.pushTelemetry(request) }
    }

    suspend fun pushRunEvents(events: List<GatewayRunEvent>): GatewayAck {
        val request =
            PushRunEventsRequest.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .addAllEvents(events)
                .setCorrelationId(Idempotency.newKey())
                .build()
        return withTimeout(config.requestTimeoutMs) { stub.pushRunEvents(request) }
    }

    suspend fun fetchConfig(): GatewayConfig {
        val request =
            FetchConfigRequest.newBuilder()
                .setTenantId(config.tenantId)
                .setGatewayId(config.gatewayId)
                .setCorrelationId(Idempotency.newKey())
                .build()
        return withTimeout(config.requestTimeoutMs) { stub.fetchConfig(request) }
    }
}
