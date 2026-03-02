package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.server.domain.device.DeviceClass
import com.neogenesis.server.domain.device.DeviceInfo
import com.neogenesis.server.domain.device.DeviceTier
import com.neogenesis.server.domain.device.effectiveCapabilities
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor

object GrpcDeviceContext {
    val deviceInfoKey: Context.Key<DeviceInfo> = Context.key("device_info")
    val capsKey: Context.Key<Set<com.neogenesis.server.domain.device.Capability>> = Context.key("device_caps")
    val methodKey: Context.Key<String> = Context.key("grpc_method")

    private val deviceIdHeader = Metadata.Key.of("x-device-id", Metadata.ASCII_STRING_MARSHALLER)
    private val deviceClassHeader = Metadata.Key.of("x-device-class", Metadata.ASCII_STRING_MARSHALLER)
    private val deviceTierHeader = Metadata.Key.of("x-device-tier", Metadata.ASCII_STRING_MARSHALLER)
    private val appVersionHeader = Metadata.Key.of("x-app-version", Metadata.ASCII_STRING_MARSHALLER)
    private val platformHeader = Metadata.Key.of("x-platform", Metadata.ASCII_STRING_MARSHALLER)
    private val osVersionHeader = Metadata.Key.of("x-os-version", Metadata.ASCII_STRING_MARSHALLER)
    private val deviceModelHeader = Metadata.Key.of("x-device-model", Metadata.ASCII_STRING_MARSHALLER)
    private val policyVersionHeader = Metadata.Key.of("x-policy-version", Metadata.ASCII_STRING_MARSHALLER)

    fun interceptor(policyRepository: DevicePolicyRepository): ServerInterceptor =
        object : ServerInterceptor {
            override fun <ReqT : Any?, RespT : Any?> interceptCall(
                call: ServerCall<ReqT, RespT>,
                headers: Metadata,
                next: ServerCallHandler<ReqT, RespT>,
            ): ServerCall.Listener<ReqT> {
                val parsedClass =
                    headers.get(deviceClassHeader)
                        ?.let { runCatching { DeviceClass.valueOf(it.trim().uppercase()) }.getOrNull() }
                val parsedTier =
                    headers.get(deviceTierHeader)
                        ?.let { runCatching { DeviceTier.valueOf(it.trim().uppercase()) }.getOrNull() }

                val info =
                    DeviceInfo(
                        deviceId = headers.get(deviceIdHeader),
                        deviceClass = parsedClass ?: DeviceClass.UNKNOWN,
                        tier = parsedTier ?: DeviceTier.TIER_2,
                        appVersion = headers.get(appVersionHeader) ?: "unknown",
                        platform = headers.get(platformHeader) ?: "unknown",
                        model = headers.get(deviceModelHeader),
                        osVersion = headers.get(osVersionHeader),
                        policyVersion = headers.get(policyVersionHeader)?.toIntOrNull(),
                    )

                val policy = policyRepository.load()
                val caps = effectiveCapabilities(info.tier, info.deviceClass, policy)
                val ctx =
                    Context.current()
                        .withValue(deviceInfoKey, info)
                        .withValue(capsKey, caps)
                        .withValue(methodKey, call.methodDescriptor.fullMethodName)
                return Contexts.interceptCall(ctx, call, headers, next)
            }
        }
}
