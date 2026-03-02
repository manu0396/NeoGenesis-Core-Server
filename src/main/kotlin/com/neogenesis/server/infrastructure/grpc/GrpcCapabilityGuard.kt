package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.device.Capability
import com.neogenesis.server.domain.model.AuditEvent
import io.grpc.Status
import org.slf4j.LoggerFactory

object GrpcCapabilityGuard {
    private val logger = LoggerFactory.getLogger("GrpcCapabilityGuard")
    @Volatile
    var auditTrailService: AuditTrailService? = null

    fun requireCapability(required: Capability) {
        val caps = GrpcDeviceContext.capsKey.get()
        val info = GrpcDeviceContext.deviceInfoKey.get()
        val method = GrpcDeviceContext.methodKey.get() ?: "unknown"
        if (caps == null || !caps.contains(required)) {
            auditTrailService?.record(
                AuditEvent(
                    tenantId = "default",
                    actor = "grpc",
                    action = "device.capability.denied",
                    resourceType = "grpc",
                    resourceId = method,
                    outcome = "denied",
                    requirementIds = emptyList(),
                    details =
                        mapOf(
                            "capability" to required.name,
                            "deviceClass" to (info?.deviceClass?.name ?: "UNKNOWN"),
                            "deviceTier" to (info?.tier?.name ?: "UNKNOWN"),
                            "deviceId" to (info?.deviceId ?: "unknown"),
                        ),
                ),
            )
            logger.warn(
                "grpc_device_capability_denied deviceClass={} tier={} method={} required={}"
                ,
                info?.deviceClass,
                info?.tier,
                method,
                required.name,
            )
            throw Status.PERMISSION_DENIED
                .withDescription("device_capability_denied: ${required.name}")
                .asRuntimeException()
        }
        auditTrailService?.record(
            AuditEvent(
                tenantId = "default",
                actor = "grpc",
                action = "device.capability.allowed",
                resourceType = "grpc",
                resourceId = method,
                outcome = "allowed",
                requirementIds = emptyList(),
                details =
                    mapOf(
                        "capability" to required.name,
                        "deviceClass" to (info?.deviceClass?.name ?: "UNKNOWN"),
                        "deviceTier" to (info?.tier?.name ?: "UNKNOWN"),
                        "deviceId" to (info?.deviceId ?: "unknown"),
                    ),
            ),
        )
    }
}
