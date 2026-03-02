package com.neogenesis.server

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

fun HttpRequestBuilder.addDeviceHeaders(
    deviceId: String = "test-device-1",
    deviceClass: String = "WINDOWS_DESKTOP",
    tier: String = "TIER_1",
    appVersion: String = "1.0.0-test",
    platform: String = "desktop",
) {
    header("X-Device-Id", deviceId)
    header("X-Device-Class", deviceClass)
    header("X-Device-Tier", tier)
    header("X-App-Version", appVersion)
    header("X-Platform", platform)
}

