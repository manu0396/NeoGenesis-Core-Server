package com.neogenesis.server.infrastructure.device

import com.neogenesis.server.domain.device.Capability
import com.neogenesis.server.domain.device.DeviceClass
import com.neogenesis.server.domain.device.DevicePolicy
import com.neogenesis.server.domain.device.DeviceTier
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

class DevicePolicyRepository(
    private val resourceName: String = "device-policy.yaml",
) {
    @Volatile
    private var cached: DevicePolicy? = null

    fun load(): DevicePolicy {
        val existing = cached
        if (existing != null) return existing
        val policy = loadFromResource() ?: DevicePolicy(version = 1)
        cached = policy
        return policy
    }

    private fun loadFromResource(): DevicePolicy? {
        val input = this::class.java.classLoader.getResourceAsStream(resourceName) ?: return null
        return parseYaml(input)
    }

    private fun parseYaml(stream: InputStream): DevicePolicy {
        val raw = Yaml().load<Map<String, Any?>>(stream)
        val version = (raw["version"] as? Number)?.toInt() ?: 1
        val minAppVersion = raw["minAppVersion"]?.toString()
        val allowTier3Alerts = raw["allowTier3Alerts"] as? Boolean
        val tierCaps = parseCapsMap(raw["tierCaps"], DeviceTier::valueOf)
        val classCaps = parseCapsMap(raw["classCaps"], DeviceClass::valueOf)
        return DevicePolicy(
            version = version,
            minAppVersion = minAppVersion,
            tierCaps = tierCaps,
            classCaps = classCaps,
            allowTier3Alerts = allowTier3Alerts,
        )
    }

    private fun <K : Enum<K>> parseCapsMap(
        raw: Any?,
        resolver: (String) -> K,
    ): Map<K, Set<Capability>>? {
        val map = raw as? Map<*, *> ?: return null
        return map.mapNotNull { (key, value) ->
            val enumKey = runCatching { resolver(key.toString().trim().uppercase()) }.getOrNull() ?: return@mapNotNull null
            val caps =
                (value as? List<*>)?.mapNotNull { cap ->
                    runCatching { Capability.valueOf(cap.toString().trim().uppercase()) }.getOrNull()
                }?.toSet() ?: emptySet()
            enumKey to caps
        }.toMap()
    }
}

