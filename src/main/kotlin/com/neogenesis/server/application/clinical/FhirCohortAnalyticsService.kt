package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.port.ClinicalDocumentStore
import com.neogenesis.server.domain.model.ClinicalDocumentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FhirCohortAnalyticsService(
    private val documentStore: ClinicalDocumentStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getCohortDemographics(
        tenantId: String,
        limit: Int = 2_000,
    ): Map<String, Int> {
        val stats = linkedMapOf<String, Int>()
        documentStore.recent(tenantId, limit)
            .asSequence()
            .filter { it.documentType == ClinicalDocumentType.FHIR }
            .forEach { doc ->
                runCatching {
                    val root = json.parseToJsonElement(doc.content).jsonObject
                    val resourceType = root["resourceType"]?.jsonPrimitive?.content
                    if (resourceType == "Patient") {
                        val gender = root["gender"]?.jsonPrimitive?.content?.lowercase() ?: "unknown"
                        stats[gender] = stats.getOrDefault(gender, 0) + 1
                    }
                }
            }
        return stats
    }

    fun getViabilityMetricsByTissue(
        tenantId: String,
        limit: Int = 2_000,
    ): Map<String, Double> {
        val grouped = mutableMapOf<String, MutableList<Double>>()
        documentStore.recent(tenantId, limit).forEach { doc ->
            val tissue = doc.metadata["tissueType"]?.ifBlank { null } ?: "retina"
            val qualityScore = doc.metadata["viabilityPrediction"]?.toDoubleOrNull()
            if (qualityScore != null) {
                grouped.computeIfAbsent(tissue) { mutableListOf() }.add(qualityScore)
            }
        }
        return grouped.mapValues { (_, values) -> values.average() }
    }
}
