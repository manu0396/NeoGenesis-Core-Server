package com.neogenesis.server.infrastructure.grpc

import io.grpc.Context
import io.grpc.Status

data class GrpcPrincipal(
    val subject: String,
    val grants: Set<String>,
    val tenantId: String?,
    val mtlsCertificateSerial: String?,
)

object GrpcAuthContextKeys {
    val principal: Context.Key<GrpcPrincipal> = Context.key("neogenesis.grpc.principal")
    val tenantId: Context.Key<String> = Context.key("neogenesis.grpc.tenant_id")
}

fun currentGrpcPrincipal(): GrpcPrincipal? = GrpcAuthContextKeys.principal.get()

fun requireGrpcGrant(vararg allowed: String): GrpcPrincipal {
    val principal = currentGrpcPrincipal() ?: throw Status.UNAUTHENTICATED.withDescription("Missing principal").asException()
    val normalizedAllowed = allowed.map { it.lowercase() }.toSet()
    if (normalizedAllowed.isNotEmpty() && principal.grants.intersect(normalizedAllowed).isEmpty()) {
        throw Status.PERMISSION_DENIED.withDescription("Insufficient gRPC role").asException()
    }
    return principal
}
