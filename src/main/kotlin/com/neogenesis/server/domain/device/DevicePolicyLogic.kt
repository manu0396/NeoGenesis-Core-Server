package com.neogenesis.server.domain.device

fun defaultCapabilitiesFor(tier: DeviceTier): Set<Capability> {
    return when (tier) {
        DeviceTier.TIER_1 -> Capability.values().toSet()
        DeviceTier.TIER_2 ->
            setOf(
                Capability.LIVE_MONITOR,
                Capability.READ_ONLY_DASHBOARD,
                Capability.ALERTS,
                Capability.QC_REVIEW,
            )
        DeviceTier.TIER_3 ->
            setOf(
                Capability.READ_ONLY_DASHBOARD,
            )
    }
}

fun effectiveCapabilities(
    tier: DeviceTier,
    deviceClass: DeviceClass,
    policy: DevicePolicy?,
): Set<Capability> {
    var current = defaultCapabilitiesFor(tier)
    val tierCaps = policy?.tierCaps?.get(tier)
    if (tierCaps != null) {
        current = current.intersect(tierCaps)
    }
    val classCaps = policy?.classCaps?.get(deviceClass)
    if (classCaps != null) {
        current = current.intersect(classCaps)
    }
    return current
}

