package com.neogenesis.server.application.clinical

import com.neogenesis.server.infrastructure.config.AppConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ClinicalValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

class ClinicalValidationService(
    private val config: AppConfig.ClinicalConfig.ValidationConfig
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun validateFhir(rawJson: String): ClinicalValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse {
                return ClinicalValidationResult(
                    valid = false,
                    errors = listOf("FHIR payload is not valid JSON"),
                    warnings = emptyList()
                )
            }

        val resourceType = root["resourceType"]?.jsonPrimitive?.content
        if (resourceType.isNullOrBlank()) {
            errors += "FHIR resourceType is required"
        }

        val fhirVersion = root["meta"]?.jsonObject?.get("versionId")?.jsonPrimitive?.content
        if (!fhirVersion.isNullOrBlank() && config.allowedFhirVersions.none { fhirVersion.startsWith(it) }) {
            errors += "FHIR versionId '$fhirVersion' is not allowed"
        }

        val profiles = root["meta"]?.jsonObject?.get("profile")?.jsonArray
            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?.toSet()
            .orEmpty()
        if (config.requiredFhirProfiles.isNotEmpty() && profiles.intersect(config.requiredFhirProfiles).isEmpty()) {
            errors += "FHIR profile mismatch. Required one of: ${config.requiredFhirProfiles.joinToString(",")}"
        }

        val hasSubject = root["subject"]?.jsonObject?.get("reference")?.jsonPrimitive?.content?.isNotBlank() == true
        val hasPatient = root["patient"]?.jsonObject?.get("reference")?.jsonPrimitive?.content?.isNotBlank() == true
        if (!hasSubject && !hasPatient) {
            errors += "FHIR patient/subject reference is required"
        }

        if (resourceType == "Observation" && root["status"] == null) {
            errors += "FHIR Observation.status is required"
        }

        if (resourceType == "DiagnosticReport" && root["code"] == null) {
            errors += "FHIR DiagnosticReport.code is required"
        }

        val detectedSystems = mutableSetOf<String>()
        collectTerminologySystems(root, detectedSystems)
        if (config.requiredTerminologySystems.isNotEmpty() && detectedSystems.isNotEmpty()) {
            val missingSystems = config.requiredTerminologySystems - detectedSystems
            if (missingSystems.isNotEmpty()) {
                errors += "FHIR terminology systems missing: ${missingSystems.joinToString(",")}"
            }
        }

        return ClinicalValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun validateHl7(rawMessage: String): ClinicalValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val lines = rawMessage.split('\r', '\n').filter { it.isNotBlank() }
        val msh = lines.firstOrNull { it.startsWith("MSH|") }?.split('|').orEmpty()
        if (msh.isEmpty()) {
            return ClinicalValidationResult(
                valid = false,
                errors = listOf("HL7 message must contain MSH segment"),
                warnings = emptyList()
            )
        }

        val messageType = msh.getOrNull(8).orEmpty()
        val version = msh.getOrNull(11).orEmpty()
        val controlId = msh.getOrNull(9).orEmpty()
        val messageTimestamp = msh.getOrNull(6).orEmpty()

        if (messageType.isBlank()) {
            errors += "HL7 MSH-9 message type is required"
        } else if (config.allowedHl7MessageTypes.isNotEmpty() && messageType !in config.allowedHl7MessageTypes) {
            errors += "HL7 message type '$messageType' is not allowed"
        }

        if (version.isBlank()) {
            errors += "HL7 MSH-12 version is required"
        } else if (config.allowedHl7Versions.isNotEmpty() && version !in config.allowedHl7Versions) {
            errors += "HL7 version '$version' is not allowed"
        }

        if (controlId.isBlank()) {
            errors += "HL7 MSH-10 control id is required"
        }

        if (messageTimestamp.isBlank()) {
            errors += "HL7 MSH-7 message timestamp is required"
        }

        val requiresPid = messageType.startsWith("ADT") || messageType.startsWith("ORM") || messageType.startsWith("ORU")
        if (requiresPid) {
            val pid = lines.firstOrNull { it.startsWith("PID|") }?.split('|').orEmpty()
            if (pid.isEmpty() || pid.getOrNull(3).isNullOrBlank()) {
                errors += "HL7 PID-3 patient identifier is required for $messageType"
            }
        }

        if (lines.none { it.startsWith("EVN|") } && messageType.startsWith("ADT")) {
            warnings += "HL7 ADT message without EVN segment"
        }

        if (messageType.startsWith("ORU") && lines.none { it.startsWith("OBX|") }) {
            errors += "HL7 ORU message requires at least one OBX segment"
        }

        return ClinicalValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    fun requireValidFhir(rawJson: String) {
        val result = validateFhir(rawJson)
        if (!result.valid && config.enforceStrict) {
            error(result.errors.joinToString("; "))
        }
    }

    fun requireValidHl7(rawMessage: String) {
        val result = validateHl7(rawMessage)
        if (!result.valid && config.enforceStrict) {
            error(result.errors.joinToString("; "))
        }
    }

    private fun collectTerminologySystems(element: JsonElement, systems: MutableSet<String>) {
        when {
            element is kotlinx.serialization.json.JsonObject -> {
                val possibleSystem = element["system"]?.jsonPrimitive?.content
                if (!possibleSystem.isNullOrBlank()) {
                    systems += possibleSystem
                }
                element.values.forEach { collectTerminologySystems(it, systems) }
            }
            element is kotlinx.serialization.json.JsonArray -> {
                element.forEach { collectTerminologySystems(it, systems) }
            }
        }
    }
}
