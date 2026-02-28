package com.neogenesis.server.infrastructure.grpc

import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import java.util.UUID

class GrpcCorrelationTracingInterceptor(
    openTelemetry: OpenTelemetry?,
) : ServerInterceptor {
    private val tracer = openTelemetry?.getTracer("neogenesis-grpc")
    private val correlationHeader = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER)
    private val tenantHeader = Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER)

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val correlationId = headers.get(correlationHeader) ?: UUID.randomUUID().toString()
        val tenantId = headers.get(tenantHeader) ?: GrpcAuthContextKeys.tenantId.get() ?: "default"

        val span =
            tracer?.spanBuilder(call.methodDescriptor.fullMethodName)
                ?.setSpanKind(SpanKind.SERVER)
                ?.setAttribute(stringKey("rpc.system"), "grpc")
                ?.setAttribute(stringKey("rpc.method"), call.methodDescriptor.fullMethodName)
                ?.setAttribute(stringKey("neogenesis.correlation_id"), correlationId)
                ?.setAttribute(stringKey("neogenesis.tenant_id"), tenantId)
                ?.startSpan()

        val wrappedCall =
            object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                override fun sendHeaders(responseHeaders: Metadata) {
                    responseHeaders.put(correlationHeader, correlationId)
                    super.sendHeaders(responseHeaders)
                }

                override fun close(
                    status: io.grpc.Status,
                    trailers: Metadata,
                ) {
                    trailers.put(correlationHeader, correlationId)
                    if (status.isOk) {
                        span?.setStatus(StatusCode.OK)
                    } else {
                        span?.setStatus(StatusCode.ERROR)
                        status.cause?.let { span?.recordException(it) }
                        span?.setAttribute(stringKey("grpc.status"), status.code.name)
                    }
                    span?.end()
                    super.close(status, trailers)
                }
            }

        return next.startCall(wrappedCall, headers)
    }
}
