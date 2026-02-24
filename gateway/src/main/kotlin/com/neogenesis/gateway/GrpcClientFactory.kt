package com.neogenesis.gateway

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
import java.io.File
import java.util.concurrent.TimeUnit

object GrpcClientFactory {
    fun build(config: GatewayConfig): ManagedChannel {
        val builder =
            if (config.useTls) {
                NettyChannelBuilder.forAddress(config.serverHost, config.serverPort)
                    .sslContext(buildSslContext(config))
            } else {
                ManagedChannelBuilder.forAddress(config.serverHost, config.serverPort).usePlaintext()
            }

        return builder
            .idleTimeout(config.heartbeatIntervalMs, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun buildSslContext(config: GatewayConfig): SslContext {
        val sslBuilder = GrpcSslContexts.forClient()
        config.serverCaPath?.let { sslBuilder.trustManager(File(it)) }

        if (config.mtlsEnabled) {
            val cert = config.clientCertPath ?: error("Missing GATEWAY_CLIENT_CERT for mTLS")
            val key = config.clientKeyPath ?: error("Missing GATEWAY_CLIENT_KEY for mTLS")
            sslBuilder.keyManager(File(cert), File(key))
        }

        return sslBuilder.build()
    }
}
