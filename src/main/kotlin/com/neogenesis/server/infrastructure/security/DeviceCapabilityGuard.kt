package com.neogenesis.server.infrastructure.security

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.device.Capability
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.device.DeviceContextFactory
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey

private val capabilityCheckedKey = AttributeKey<Boolean>("device_capability_checked")
private val requiredCapabilityKey = AttributeKey<Capability>("device_required_capability")

suspend fun ApplicationCall.requireCapability(
    required: Capability,
    policyRepository: DevicePolicyRepository,
    auditTrailService: AuditTrailService,
): Boolean {
    attributes.put(capabilityCheckedKey, true)
    attributes.put(requiredCapabilityKey, required)
    val ctx = DeviceContextFactory.fromCall(this, policyRepository)
    if (!ctx.effectiveCapabilities.contains(required)) {
        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId(),
                actor = actor(),
                action = "device.capability.denied",
                resourceType = "http",
                resourceId = "${request.httpMethod.value} ${request.path()}",
                outcome = "denied",
                requirementIds = emptyList(),
                details =
                    mapOf(
                        "capability" to required.name,
                        "deviceClass" to ctx.deviceInfo.deviceClass.name,
                        "deviceTier" to ctx.deviceInfo.tier.name,
                        "deviceId" to (ctx.deviceInfo.deviceId ?: "unknown"),
                        "path" to request.path(),
                        "method" to request.httpMethod.value,
                    ),
            ),
        )
        throw SecurityPluginException(
            status = HttpStatusCode.Forbidden,
            code = "device_capability_denied",
        )
    }
    auditTrailService.record(
        AuditEvent(
            tenantId = tenantId(),
            actor = actor(),
            action = "device.capability.allowed",
            resourceType = "http",
            resourceId = "${request.httpMethod.value} ${request.path()}",
            outcome = "allowed",
            requirementIds = emptyList(),
            details =
                mapOf(
                    "capability" to required.name,
                    "deviceClass" to ctx.deviceInfo.deviceClass.name,
                    "deviceTier" to ctx.deviceInfo.tier.name,
                    "deviceId" to (ctx.deviceInfo.deviceId ?: "unknown"),
                    "path" to request.path(),
                    "method" to request.httpMethod.value,
                ),
        ),
    )
    return true
}

fun ApplicationCall.hasCapabilityChecked(): Boolean =
    attributes.getOrNull(capabilityCheckedKey) == true

fun ApplicationCall.requiredCapabilityOrNull(): Capability? =
    attributes.getOrNull(requiredCapabilityKey)
