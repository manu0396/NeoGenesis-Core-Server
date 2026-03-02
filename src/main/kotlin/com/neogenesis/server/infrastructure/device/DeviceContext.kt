package com.neogenesis.server.infrastructure.device

import com.neogenesis.server.domain.device.DeviceClass
import com.neogenesis.server.domain.device.DeviceInfo
import com.neogenesis.server.domain.device.DeviceTier
import com.neogenesis.server.domain.device.effectiveCapabilities
import io.ktor.server.application.ApplicationCall

data class DeviceContext(
    val deviceInfo: DeviceInfo,
    val effectiveCapabilities: Set<com.neogenesis.server.domain.device.Capability>,
)

object DeviceContextFactory {
    private const val HEADER_DEVICE_ID = "X-Device-Id"
    private const val HEADER_DEVICE_CLASS = "X-Device-Class"
    private const val HEADER_DEVICE_TIER = "X-Device-Tier"
    private const val HEADER_APP_VERSION = "X-App-Version"
    private const val HEADER_PLATFORM = "X-Platform"
    private const val HEADER_OS_VERSION = "X-OS-Version"
    private const val HEADER_DEVICE_MODEL = "X-Device-Model"
    private const val HEADER_POLICY_VERSION = "X-Policy-Version"

    fun fromCall(call: ApplicationCall, policyRepository: DevicePolicyRepository): DeviceContext {
        val parsedClass =
            call.request.headers[HEADER_DEVICE_CLASS]
                ?.let { runCatching { DeviceClass.valueOf(it.trim().uppercase()) }.getOrNull() }
        val parsedTier =
            call.request.headers[HEADER_DEVICE_TIER]
                ?.let { runCatching { DeviceTier.valueOf(it.trim().uppercase()) }.getOrNull() }

        val info =
            DeviceInfo(
                deviceId = call.request.headers[HEADER_DEVICE_ID],
                deviceClass = parsedClass ?: DeviceClass.UNKNOWN,
                tier = parsedTier ?: DeviceTier.TIER_2,
                appVersion = call.request.headers[HEADER_APP_VERSION] ?: "unknown",
                platform = call.request.headers[HEADER_PLATFORM] ?: "unknown",
                model = call.request.headers[HEADER_DEVICE_MODEL],
                osVersion = call.request.headers[HEADER_OS_VERSION],
                policyVersion = call.request.headers[HEADER_POLICY_VERSION]?.toIntOrNull(),
            )
        val policy = policyRepository.load()
        val caps = effectiveCapabilities(info.tier, info.deviceClass, policy)
        return DeviceContext(info, caps)
    }
}

