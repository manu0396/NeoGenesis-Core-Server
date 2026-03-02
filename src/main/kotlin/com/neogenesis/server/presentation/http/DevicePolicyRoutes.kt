package com.neogenesis.server.presentation.http

import com.neogenesis.server.domain.device.DeviceClass
import com.neogenesis.server.domain.device.DeviceInfo
import com.neogenesis.server.domain.device.DevicePolicy
import com.neogenesis.server.domain.device.DeviceTier
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
private data class DeviceInfoRequest(
    val deviceId: String? = null,
    val deviceClass: String,
    val tier: String,
    val appVersion: String,
    val platform: String,
    val model: String? = null,
    val osVersion: String? = null,
    val policyVersion: Int? = null,
)

fun Route.devicePolicyRoutes(policyRepository: DevicePolicyRepository) {
    get("/api/v1/device-policy") {
        call.respond(policyRepository.load())
    }

    post("/api/v1/device/register") {
        val req = call.receive<DeviceInfoRequest>()
        val parsedClass = runCatching { DeviceClass.valueOf(req.deviceClass.trim().uppercase()) }.getOrNull()
        val parsedTier = runCatching { DeviceTier.valueOf(req.tier.trim().uppercase()) }.getOrNull()
        if (parsedClass == null || parsedTier == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_device_info"))
            return@post
        }
        val info =
            DeviceInfo(
                deviceId = req.deviceId,
                deviceClass = parsedClass,
                tier = parsedTier,
                appVersion = req.appVersion,
                platform = req.platform,
                model = req.model,
                osVersion = req.osVersion,
                policyVersion = req.policyVersion,
            )
        call.respond(call.devicePolicyResponse(policyRepository, info))
    }
}

private fun ApplicationCall.devicePolicyResponse(
    policyRepository: DevicePolicyRepository,
    @Suppress("UNUSED_PARAMETER") info: DeviceInfo,
): DevicePolicy = policyRepository.load()
