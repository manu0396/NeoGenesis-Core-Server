package com.neogenesis.server.infrastructure.grpc

import com.google.protobuf.Empty
import com.neogenesis.device.policy.CapabilitySet
import com.neogenesis.device.policy.DevicePolicy
import com.neogenesis.device.policy.DevicePolicyServiceGrpcKt
import com.neogenesis.device.policy.DeviceInfo as ProtoDeviceInfo
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository

class DevicePolicyGrpcService(
    private val policyRepository: DevicePolicyRepository,
) : DevicePolicyServiceGrpcKt.DevicePolicyServiceCoroutineImplBase() {
    override suspend fun getDevicePolicy(request: Empty): DevicePolicy {
        return policyRepository.load().toProto()
    }

    override suspend fun registerDevice(request: ProtoDeviceInfo): DevicePolicy {
        return policyRepository.load().toProto()
    }
}

private fun com.neogenesis.server.domain.device.DevicePolicy.toProto(): DevicePolicy {
    val builder = DevicePolicy.newBuilder()
        .setVersion(version)
    minAppVersion?.let { builder.minAppVersion = it }
    allowTier3Alerts?.let { builder.allowTier3Alerts = it }
    tierCaps?.forEach { (tier, caps) ->
        builder.putTierCaps(tier.name, CapabilitySet.newBuilder().addAllCapabilities(caps.map { it.name }).build())
    }
    classCaps?.forEach { (clazz, caps) ->
        builder.putClassCaps(clazz.name, CapabilitySet.newBuilder().addAllCapabilities(caps.map { it.name }).build())
    }
    return builder.build()
}

