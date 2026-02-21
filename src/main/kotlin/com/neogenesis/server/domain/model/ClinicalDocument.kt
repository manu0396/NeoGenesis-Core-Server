package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ClinicalDocumentType {
    DICOM,
    HL7,
    FHIR
}

@Serializable
data class ClinicalDocument(
    val documentType: ClinicalDocumentType,
    val externalId: String?,
    val patientId: String?,
    val content: String,
    val metadata: Map<String, String>,
    val createdAtMs: Long = System.currentTimeMillis()
)
