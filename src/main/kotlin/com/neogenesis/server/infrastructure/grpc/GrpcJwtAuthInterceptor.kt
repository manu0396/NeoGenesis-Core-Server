package com.neogenesis.server.infrastructure.grpc

import com.auth0.jwt.JWTVerifier
import io.grpc.Contexts
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import java.security.cert.X509Certificate

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
            val subject = decoded.subject ?: "grpc-client"
            val claim = decoded.getClaim("roles")
            val roles =
                claim.asList(String::class.java)
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.map { it.lowercase() }
                    ?.toSet()
                    ?: claim.asString()
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.map { it.lowercase() }
                        ?.toSet()
                        .orEmpty()
            val scopes =
                decoded.getClaim("scope").asString()
                    ?.split(' ')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.map { it.lowercase() }
                    ?.toSet()
                    .orEmpty()
            val authzGrants = roles + scopes

            if (authzGrants.intersect(REQUIRED_GRANTS).isEmpty()) {
                call.close(Status.PERMISSION_DENIED.withDescription("Insufficient gRPC role"), Metadata())
                return object : ServerCall.Listener<ReqT>() {}
            }

            val principal =
                GrpcPrincipal(
                    subject = subject,
                    grants = authzGrants,
                    tenantId = decoded.getClaim("tenantId").asString(),
                    mtlsCertificateSerial = resolvePeerCertificateSerial(call),
                )
            val context = io.grpc.Context.current().withValue(GrpcAuthContextKeys.principal, principal)
            Contexts.interceptCall(context, call, headers, next)
        } catch (_: Exception) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid bearer token"), Metadata())
            object : ServerCall.Listener<ReqT>() {}
        }
    }

    companion object {
        private val REQUIRED_GRANTS =
            setOf(
                "firmware",
                "controller",
                "gateway",
                "regenops_operator",
                "regenops_auditor",
                "admin",
                "operator",
                "auditor",
                "integration",
                "sre",
                "quality_manager",
            )
        private val AUTHORIZATION_HEADER: Metadata.Key<String> =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
    }
}

private fun resolvePeerCertificateSerial(call: ServerCall<*, *>): String? {
    val sslSession = call.attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION) ?: return null
    val certificate =
        runCatching {
            sslSession.peerCertificates.firstOrNull() as? X509Certificate
        }.getOrNull()
            ?: return null
    return certificate.serialNumber.toString(16).lowercase()
}
