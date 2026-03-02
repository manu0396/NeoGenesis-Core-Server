package com.neogenesis.server.infrastructure.security

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.domain.device.Capability
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.infrastructure.device.DeviceContextFactory
import com.neogenesis.server.infrastructure.device.DevicePolicyRepository
import io.ktor.http.HttpMethod
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path

data class DeviceCapabilityMapping(
    val method: HttpMethod,
    val path: Regex,
    val capability: Capability,
)

class DeviceCapabilityEnforcementConfig {
    var auditTrailService: AuditTrailService? = null
    var policyRepository: DevicePolicyRepository? = null
    val allowlistPaths: MutableList<Regex> = mutableListOf()
    val mappings: MutableList<DeviceCapabilityMapping> = mutableListOf()
}

val DeviceCapabilityEnforcement =
    createApplicationPlugin(
        name = "DeviceCapabilityEnforcement",
        createConfiguration = ::DeviceCapabilityEnforcementConfig,
    ) {
        val auditTrailService = pluginConfig.auditTrailService
        val policyRepository = pluginConfig.policyRepository
        val allowlist = pluginConfig.allowlistPaths.toList()
        val mappings = pluginConfig.mappings.toList()
        val mutatingMethods = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Patch)

        onCall { call ->
            val path = call.request.path()
            val method = call.request.httpMethod
            if (allowlist.any { it.matches(path) }) return@onCall

            val mapping = mappings.firstOrNull { it.method == method && it.path.matches(path) }
            if (mapping != null) {
                val policyRepo = policyRepository ?: return@onCall
                val audit = auditTrailService ?: return@onCall
                if (!call.requireCapability(mapping.capability, policyRepo, audit)) return@onCall
                return@onCall
            }

            if (mutatingMethods.contains(method)) {
                val ctx = policyRepository?.let { DeviceContextFactory.fromCall(call, it) }
                auditTrailService?.record(
                    AuditEvent(
                        tenantId = call.tenantId(),
                        actor = call.actor(),
                        action = "device.capability.unmapped",
                        resourceType = "http",
                        resourceId = "${method.value} $path",
                        outcome = "denied",
                        requirementIds = emptyList(),
                        details =
                            mapOf(
                                "path" to path,
                                "method" to method.value,
                                "deviceClass" to (ctx?.deviceInfo?.deviceClass?.name ?: "UNKNOWN"),
                                "deviceTier" to (ctx?.deviceInfo?.tier?.name ?: "UNKNOWN"),
                                "deviceId" to (ctx?.deviceInfo?.deviceId ?: "unknown"),
                            ),
                    ),
                )
                throw SecurityPluginException(
                    status = io.ktor.http.HttpStatusCode.Forbidden,
                    code = "device_capability_unmapped",
                )
            }
        }
    }

fun defaultDeviceCapabilityMappings(): List<DeviceCapabilityMapping> {
    return listOf(
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/telemetry$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/telemetry/[^/]+$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/telemetry/history$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/commands/history$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/telemetry/evaluate$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/telemetry/job/[^/]+$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/demo/simulator/runs$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/twin/job/[^/]+$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/print-sessions$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/print-sessions/[^/]+/activate$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/print-sessions/[^/]+/complete$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/print-sessions/[^/]+/abort$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/regenops/runs/start$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/regenops/runs/[^/]+/pause$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/regenops/runs/[^/]+/abort$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/runs/start$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/runs/[^/]+/control$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/runs/start$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/runs/[^/]+/pause$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/runs/[^/]+/abort$"), Capability.PRINT_CONTROL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/regenops/protocols$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/regenops/protocols/[^/]+/publish$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/protocols/[^/]+/versions/[^/]+/publish$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/protocols/[^/]+/publish$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/protocols/.+"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Put, Regex("^/api/v1/protocols/.+"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Delete, Regex("^/api/v1/protocols/.+"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/recipes$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/api/v1/recipes/[^/]+/activate$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Put, Regex("^/api/v1/recipes/[^/]+$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Delete, Regex("^/api/v1/recipes/[^/]+$"), Capability.PROTOCOL_EDIT),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/admin/compliance/protocols/[^/]+/publish-approvals$"), Capability.QC_APPROVAL),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/admin/compliance/publish-approvals/[^/]+/approve$"), Capability.QC_APPROVAL),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/print-sessions/active$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/regenops/protocols$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/regenops/protocols/[^/]+/status$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/regenops/runs$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/runs$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/protocols$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/protocols/[^/]+$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/print-jobs$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/print-jobs/[^/]+/status$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/devices$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/devices$"), Capability.READ_ONLY_DASHBOARD),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/devices.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/digital-twin/[^/]+$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/digital-twin$"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/telemetry/.+"), Capability.LIVE_MONITOR),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/runs/[^/]+/events$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/telemetry/[^/]+/export$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/evidence/[^/]+/package$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/audit-bundle/job/[^/]+\\.zip$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/metrics/drift-alerts$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/api/v1/metrics/reproducibility-score$"), Capability.QC_REVIEW),
        DeviceCapabilityMapping(HttpMethod.Get, Regex("^/admin/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/admin/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Put, Regex("^/admin/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Delete, Regex("^/admin/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/clinical/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/gdpr/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/regulatory/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/integration/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/billing/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/commercial/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/benchmark/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/bioink/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/jobs/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Post, Regex("^/jobs$"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Put, Regex("^/jobs/.*"), Capability.ADMIN_SETTINGS),
        DeviceCapabilityMapping(HttpMethod.Delete, Regex("^/jobs/.*"), Capability.ADMIN_SETTINGS),
    )
}


