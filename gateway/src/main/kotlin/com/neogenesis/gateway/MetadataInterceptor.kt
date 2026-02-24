package com.neogenesis.gateway

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import java.util.UUID

class MetadataInterceptor(
    private val config: GatewayConfig,
) : ClientInterceptor {
    private val tenantHeader = Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER)
    private val gatewayHeader = Metadata.Key.of("x-gateway-id", Metadata.ASCII_STRING_MARSHALLER)
    private val correlationHeader = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> {
        val call = next.newCall(method, callOptions)
        return object : ClientCall<ReqT, RespT>() {
            override fun start(responseListener: Listener<RespT>, headers: Metadata) {
                headers.put(tenantHeader, config.tenantId)
                headers.put(gatewayHeader, config.gatewayId)
                headers.put(correlationHeader, UUID.randomUUID().toString())
                call.start(responseListener, headers)
            }

            override fun request(numMessages: Int) = call.request(numMessages)

            override fun cancel(message: String?, cause: Throwable?) = call.cancel(message, cause)

            override fun halfClose() = call.halfClose()

            override fun sendMessage(message: ReqT) = call.sendMessage(message)
        }
    }
}
