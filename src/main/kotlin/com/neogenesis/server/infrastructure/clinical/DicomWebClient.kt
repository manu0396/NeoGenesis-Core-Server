package com.neogenesis.server.infrastructure.clinical

import com.neogenesis.server.infrastructure.config.AppConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration

data class DicomWebStudyMetadata(
    val studyInstanceUid: String,
    val metadataJson: String
)

class DicomWebClient(
    private val config: AppConfig.ClinicalConfig.DicomWebConfig
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.timeoutMs))
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun isEnabled(): Boolean = config.enabled && !config.baseUrl.isNullOrBlank()

    fun queryStudies(patientId: String, limit: Int = 5): String {
        require(isEnabled()) { "DICOMweb integration is disabled or misconfigured" }
        val encodedPatient = URLEncoder.encode(patientId, StandardCharsets.UTF_8)
        val uri = URI.create("${config.baseUrl!!.trimEnd('/')}/studies?PatientID=$encodedPatient&limit=$limit")
        return get(uri)
    }

    fun fetchLatestStudyMetadata(patientId: String): DicomWebStudyMetadata? {
        val studiesRaw = queryStudies(patientId, limit = 1)
        val studies = runCatching { json.parseToJsonElement(studiesRaw).jsonArray }.getOrElse { JsonArray(emptyList()) }
        val first = studies.firstOrNull() ?: return null
        val studyUid = extractStudyInstanceUid(first.jsonObject) ?: return null
        val metadataUri = URI.create("${config.baseUrl!!.trimEnd('/')}/studies/$studyUid/metadata")
        val metadata = get(metadataUri)
        return DicomWebStudyMetadata(studyInstanceUid = studyUid, metadataJson = metadata)
    }

    private fun get(uri: URI): String {
        val requestBuilder = HttpRequest.newBuilder(uri)
            .GET()
            .timeout(Duration.ofMillis(config.timeoutMs))
            .header("Accept", "application/dicom+json, application/json")
        if (!config.bearerToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.bearerToken}")
        }
        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error("DICOMweb call failed status=${response.statusCode()} uri=$uri")
        }
        return response.body()
    }

    private fun extractStudyInstanceUid(study: JsonObject): String? {
        val standardTag = study["0020000D"]
            ?.jsonObject
            ?.get("Value")
            ?.jsonArray
            ?.firstOrNull()
            ?.let { asString(it) }
        if (!standardTag.isNullOrBlank()) {
            return standardTag
        }
        return study["StudyInstanceUID"]?.let { asString(it) }
    }

    private fun asString(element: JsonElement): String? {
        return when (element) {
            is JsonObject -> null
            is JsonArray -> element.firstOrNull()?.let { asString(it) }
            else -> element.toString().trim('"').ifBlank { element.jsonPrimitive.contentOrNull }
        }
    }
}
