package com.neogenesis.server.infrastructure.grpc

import com.auth0.jwt.JWTVerifier
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status

class GrpcJwtAuthInterceptor(
    private val verifier: JWTVerifier,
) : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>,
    ): ServerCall.Listener<ReqT> {
        val rawHeader = headers.get(AUTHORIZATION_HEADER)
        val token =
            rawHeader
                ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substringAfter(' ')

        if (token.isNullOrBlank()) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing bearer token"), Metadata())
            return object : ServerCall.Listener<ReqT>() {}
        }

        return try {
            val decoded = verifier.verify(token)
            val claim = decoded.getClaim("roles")
            val roles =
                claim.asList(String::class.java)
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: claim.asString()
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                        .orEmpty()
            val scopes =
                decoded.getClaim("scope").asString()
                    ?.split(' ')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    .orEmpty()
            val authzGrants = roles + scopes

            if (authzGrants.intersect(REQUIRED_ROLES).isEmpty()) {
                call.close(Status.PERMISSION_DENIED.withDescription("Insufficient gRPC role"), Metadata())
                return object : ServerCall.Listener<ReqT>() {}
            }

            next.startCall(call, headers)
        } catch (_: Exception) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid bearer token"), Metadata())
            object : ServerCall.Listener<ReqT>() {}
        }
    }

    companion object {
        private val REQUIRED_ROLES = setOf("firmware", "controller")
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}
