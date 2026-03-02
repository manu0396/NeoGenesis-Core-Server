package com.neogenesis.server.domain.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevicePolicyLogicTest {
    @Test
    fun `default capabilities match tiers`() {
        assertTrue(defaultCapabilitiesFor(DeviceTier.TIER_1).contains(Capability.PRINT_CONTROL))
        assertTrue(defaultCapabilitiesFor(DeviceTier.TIER_2).contains(Capability.READ_ONLY_DASHBOARD))
        assertTrue(defaultCapabilitiesFor(DeviceTier.TIER_3).contains(Capability.READ_ONLY_DASHBOARD))
        assertTrue(defaultCapabilitiesFor(DeviceTier.TIER_3).contains(Capability.ADMIN_SETTINGS).not())
    }

    @Test
    fun `effective capabilities are narrowed by policy`() {
        val policy =
            DevicePolicy(
                version = 1,
                tierCaps = mapOf(DeviceTier.TIER_2 to setOf(Capability.READ_ONLY_DASHBOARD)),
                classCaps = mapOf(DeviceClass.ANDROID_TABLET to setOf(Capability.READ_ONLY_DASHBOARD)),
            )
        val caps = effectiveCapabilities(DeviceTier.TIER_2, DeviceClass.ANDROID_TABLET, policy)
        assertEquals(setOf(Capability.READ_ONLY_DASHBOARD), caps)
    }
}

