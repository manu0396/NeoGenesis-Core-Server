package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.server.infrastructure.persistence.TenantContext
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

class GrpcTenantInterceptor : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val principal = GrpcAuthContextKeys.principal.get()
        val tenantId = principal?.tenantId ?: headers.get(TENANT_ID_HEADER) ?: "default"

        val context = Context.current().withValue(GrpcAuthContextKeys.tenantId, tenantId)
        TenantContext.set(tenantId)
        
        return Contexts.interceptCall(context, call, headers, next)
    }

    companion object {
        private val TENANT_ID_HEADER = Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER)
    }
}
