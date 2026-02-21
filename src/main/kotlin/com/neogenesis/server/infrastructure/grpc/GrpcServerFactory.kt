package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.security.SslHotReloadManager
import io.grpc.Server
import io.grpc.ServerServiceDefinition
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import java.io.File

object GrpcServerFactory {
    data class GrpcServerRuntime(
        val server: Server,
        val sslHotReloadManager: SslHotReloadManager?
    )

    fun build(
        grpcPort: Int,
        tlsConfig: AppConfig.SecurityConfig.MtlsConfig.GrpcMtlsConfig,
        serviceDefinition: ServerServiceDefinition
    ): GrpcServerRuntime {
        val builder = NettyServerBuilder.forPort(grpcPort)
            .addService(serviceDefinition)
        var hotReloadManager: SslHotReloadManager? = null

        if (tlsConfig.enabled) {
            require(!tlsConfig.certChainPath.isNullOrBlank()) { "gRPC mTLS certChainPath is required" }
            require(!tlsConfig.privateKeyPath.isNullOrBlank()) { "gRPC mTLS privateKeyPath is required" }

            val sslBuilder = if (tlsConfig.hotReloadEnabled) {
                hotReloadManager = SslHotReloadManager(
                    certPath = tlsConfig.certChainPath,
                    keyPath = tlsConfig.privateKeyPath,
                    reloadIntervalMinutes = tlsConfig.hotReloadIntervalMinutes
                ).also { it.start() }
                GrpcSslContexts.configure(SslContextBuilder.forServer(hotReloadManager.keyManager))
            } else {
                GrpcSslContexts.forServer(
                    File(tlsConfig.certChainPath),
                    File(tlsConfig.privateKeyPath)
                )
            }

            if (!tlsConfig.trustCertPath.isNullOrBlank()) {
                sslBuilder.trustManager(File(tlsConfig.trustCertPath))
            }

            if (tlsConfig.requireClientAuth) {
                sslBuilder.clientAuth(ClientAuth.REQUIRE)
            }

            builder.sslContext(sslBuilder.build())
        }

        return GrpcServerRuntime(
            server = builder.build(),
            sslHotReloadManager = hotReloadManager
        )
    }
}
