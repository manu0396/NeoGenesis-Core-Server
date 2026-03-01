package com.neogenesis.server.application.regenops

import com.neogenesis.server.application.error.BadRequestException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ProtocolDsl(
    val dslVersion: String,
    val graph: ProtocolGraph? = null,
    val capabilities: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap(),
    val raw: JsonElement? = null,
)

@Serializable
data class ProtocolGraph(
    val nodes: List<ProtocolNode>,
    val edges: List<ProtocolEdge>,
)

@Serializable
data class ProtocolNode(
    val id: String,
    val type: String,
    val params: JsonObject = buildJsonObject { },
    val requiredCapabilities: Set<String> = emptySet(),
)

@Serializable
data class ProtocolEdge(
    val from: String,
    val to: String,
)

data class ProtocolValidationResult(
    val valid: Boolean,
    val violations: List<String>,
)

private val json = Json { ignoreUnknownKeys = true }

fun wrapProtocolDsl(contentJson: String): ProtocolDsl {
    val parsed = parseDsl(contentJson)
    if (parsed != null) return parsed
    return ProtocolDsl(
        dslVersion = "legacy",
        raw = runCatching { json.parseToJsonElement(contentJson) }.getOrNull(),
    )
}

fun serializeProtocolDsl(dsl: ProtocolDsl): String {
    return json.encodeToString(ProtocolDsl.serializer(), dsl)
}

fun validateProtocolDsl(dsl: ProtocolDsl): ProtocolValidationResult {
    if (dsl.dslVersion != "1") {
        return ProtocolValidationResult(valid = true, violations = emptyList())
    }
    val graph = dsl.graph ?: return ProtocolValidationResult(false, listOf("graph is required"))
    val nodeIds = graph.nodes.map { it.id }.toSet()
    val violations = mutableListOf<String>()

    graph.nodes.forEach { node ->
        if (node.id.isBlank()) violations += "node.id required"
        if (node.type.isBlank()) violations += "node.type required for ${node.id}"
        val requiredParams = requiredParamsFor(node.type)
        requiredParams.forEach { param ->
            if (!node.params.containsKey(param)) {
                violations += "missing param '$param' for node ${node.id}"
            }
        }
        val requiredCaps = requiredCapabilitiesFor(node)
        if (!dsl.capabilities.containsAll(requiredCaps)) {
            violations += "node ${node.id} requires capabilities ${requiredCaps.joinToString(",")}"
        }
        validateUnsafe(node)?.let { violations += it }
    }

    graph.edges.forEach { edge ->
        if (!nodeIds.contains(edge.from)) violations += "edge.from missing node ${edge.from}"
        if (!nodeIds.contains(edge.to)) violations += "edge.to missing node ${edge.to}"
        if (edge.from == edge.to) violations += "self-cycle at ${edge.from}"
    }

    if (detectCycle(graph)) {
        violations += "graph contains cycle"
    }

    return ProtocolValidationResult(valid = violations.isEmpty(), violations = violations)
}

fun requireValidProtocolDsl(dsl: ProtocolDsl) {
    val result = validateProtocolDsl(dsl)
    if (!result.valid) {
        throw BadRequestException("protocol_invalid", "Protocol DSL invalid: ${result.violations.joinToString("; ")}")
    }
}

private fun parseDsl(contentJson: String): ProtocolDsl? {
    return runCatching {
        json.decodeFromString(ProtocolDsl.serializer(), contentJson)
    }.getOrNull()
}

private fun requiredParamsFor(type: String): Set<String> {
    return when (type.lowercase()) {
        "extrude" -> setOf("pressureKpa", "durationMs")
        "incubate" -> setOf("durationMs", "temperatureC")
        "sterilize" -> setOf("durationMs", "temperatureC")
        "scan" -> setOf("resolution")
        else -> emptySet()
    }
}

private fun requiredCapabilitiesFor(node: ProtocolNode): Set<String> {
    if (node.requiredCapabilities.isNotEmpty()) return node.requiredCapabilities
    return when (node.type.lowercase()) {
        "extrude" -> setOf("pressure")
        "incubate", "sterilize" -> setOf("thermal")
        "scan" -> setOf("imaging")
        else -> emptySet()
    }
}

private fun validateUnsafe(node: ProtocolNode): String? {
    if (node.type.equals("override_safety", ignoreCase = true)) {
        return "node ${node.id} uses unsafe step override_safety"
    }
    if (node.type.equals("incubate", ignoreCase = true) || node.type.equals("sterilize", ignoreCase = true)) {
        val temp = node.params["temperatureC"]?.asDouble()
        if (temp != null && temp > 60.0) {
            return "node ${node.id} temperatureC $temp exceeds safe limit"
        }
    }
    return null
}

private fun JsonElement.asDouble(): Double? {
    return (this as? JsonPrimitive)?.doubleOrNull
}

private fun detectCycle(graph: ProtocolGraph): Boolean {
    val adjacency = graph.edges.groupBy { it.from }.mapValues { it.value.map { edge -> edge.to } }
    val visited = mutableSetOf<String>()
    val stack = mutableSetOf<String>()

    fun dfs(node: String): Boolean {
        if (stack.contains(node)) return true
        if (visited.contains(node)) return false
        visited += node
        stack += node
        adjacency[node].orEmpty().forEach { next ->
            if (dfs(next)) return true
        }
        stack.remove(node)
        return false
    }

    return graph.nodes.any { dfs(it.id) }
}
