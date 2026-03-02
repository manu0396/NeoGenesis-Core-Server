package com.neogenesis.server.domain.device

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceTier {
    TIER_1,
    TIER_2,
    TIER_3,
}

@Serializable
enum class DeviceClass {
    WINDOWS_DESKTOP,
    MAC_DESKTOP,
    IPAD_TABLET,
    ANDROID_TABLET,
    IOS_PHONE,
    ANDROID_PHONE,
    EMBEDDED_TOUCHSCREEN,
    TV_DISPLAY,
    UNKNOWN,
}

@Serializable
enum class Capability {
    PRINT_CONTROL,
    PROTOCOL_EDIT,
    QC_REVIEW,
    QC_APPROVAL,
    LIVE_MONITOR,
    ALERTS,
    READ_ONLY_DASHBOARD,
    ADMIN_SETTINGS,
}

@Serializable
data class DeviceInfo(
    val deviceId: String?,
    val deviceClass: DeviceClass,
    val tier: DeviceTier,
    val appVersion: String,
    val platform: String,
    val model: String?,
    val osVersion: String?,
    val policyVersion: Int?,
)

@Serializable
data class DevicePolicy(
    val version: Int,
    val minAppVersion: String? = null,
    val tierCaps: Map<DeviceTier, Set<Capability>>? = null,
    val classCaps: Map<DeviceClass, Set<Capability>>? = null,
    val allowTier3Alerts: Boolean? = null,
)
