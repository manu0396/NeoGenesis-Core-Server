package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.compliance.GdprService
import com.neogenesis.server.application.port.ClinicalDocumentStore
import com.neogenesis.server.application.serverless.ServerlessDispatchService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.domain.model.ClinicalDocumentType
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ClinicalIntegrationService(
    private val clinicalDocumentStore: ClinicalDocumentStore,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService,
    private val serverlessDispatchService: ServerlessDispatchService,
    private val validationService: ClinicalValidationService,
    private val gdprService: GdprService,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun ingestFhir(
        tenantId: String,
        rawJson: String,
        actor: String,
    ): ClinicalDocument {
        validationService.requireValidFhir(rawJson)
        val parsed = json.parseToJsonElement(rawJson).jsonObject
        val resourceType = parsed["resourceType"]?.jsonPrimitive?.content.orEmpty()
        val externalId = parsed["id"]?.jsonPrimitive?.content
        val patientReference =
            parsed["subject"]?.jsonObject?.get("reference")?.jsonPrimitive?.content
                ?: parsed["patient"]?.jsonObject?.get("reference")?.jsonPrimitive?.content
        requireConsent(tenantId, patientReference, "CLINICAL_PROCESSING")

        val document =
            ClinicalDocument(
                tenantId = tenantId,
                documentType = ClinicalDocumentType.FHIR,
                externalId = externalId,
                patientId = patientReference,
                content = rawJson,
                metadata =
                    mapOf(
                        "resourceType" to resourceType.ifBlank { "Unknown" },
                        "dataClassification" to "PHI",
                        "processingPurpose" to "CLINICAL_PROCESSING",
                    ),
            )

        persistAndAudit(document, actor, "clinical.ingest.fhir")
        metricsService.recordClinicalDocument("FHIR")
        dispatch("FHIR_INGESTED", document)
        return document
    }

    fun ingestHl7(
        tenantId: String,
        rawMessage: String,
        actor: String,
    ): ClinicalDocument {
        validationService.requireValidHl7(rawMessage)
        val lines = rawMessage.split('\n', '\r').filter { it.isNotBlank() }
        val msh = lines.firstOrNull { it.startsWith("MSH|") }?.split('|').orEmpty()
        val pid = lines.firstOrNull { it.startsWith("PID|") }?.split('|').orEmpty()

        val messageType = msh.getOrNull(8).orEmpty()
        val externalId = msh.getOrNull(9)
        val patientId = pid.getOrNull(3)
        requireConsent(tenantId, patientId, "CLINICAL_PROCESSING")

        val document =
            ClinicalDocument(
                tenantId = tenantId,
                documentType = ClinicalDocumentType.HL7,
                externalId = externalId,
                patientId = patientId,
                content = rawMessage,
                metadata =
                    mapOf(
                        "messageType" to messageType.ifBlank { "Unknown" },
                        "dataClassification" to "PHI",
                        "processingPurpose" to "CLINICAL_PROCESSING",
                    ),
            )

        persistAndAudit(document, actor, "clinical.ingest.hl7")
        metricsService.recordClinicalDocument("HL7")
        dispatch("HL7_INGESTED", document)
        return document
    }

    fun ingestDicomMetadata(
        tenantId: String,
        rawJson: String,
        actor: String,
    ): ClinicalDocument {
        val parsed = json.parseToJsonElement(rawJson).jsonObject
        val sopInstanceUid = parsed["sopInstanceUid"]?.jsonPrimitive?.content
        val patientId = parsed["patientId"]?.jsonPrimitive?.content
        val modality = parsed["modality"]?.jsonPrimitive?.content.orEmpty()
        requireConsent(tenantId, patientId, "CLINICAL_PROCESSING")

        val document =
            ClinicalDocument(
                tenantId = tenantId,
                documentType = ClinicalDocumentType.DICOM,
                externalId = sopInstanceUid,
                patientId = patientId,
                content = rawJson,
                metadata =
                    mapOf(
                        "modality" to modality.ifBlank { "Unknown" },
                        "dataClassification" to "PHI",
                        "processingPurpose" to "CLINICAL_PROCESSING",
                    ),
            )

        persistAndAudit(document, actor, "clinical.ingest.dicom")
        metricsService.recordClinicalDocument("DICOM")
        dispatch("DICOM_INGESTED", document)
        return document
    }

    fun ingestDicomWebMetadata(
        tenantId: String,
        patientId: String,
        studyInstanceUid: String,
        metadataJson: String,
        actor: String,
    ): ClinicalDocument {
        requireConsent(tenantId, patientId, "CLINICAL_PROCESSING")
        val document =
            ClinicalDocument(
                tenantId = tenantId,
                documentType = ClinicalDocumentType.DICOM,
                externalId = studyInstanceUid,
                patientId = patientId,
                content = metadataJson,
                metadata =
                    mapOf(
                        "source" to "DICOMWEB",
                        "studyInstanceUid" to studyInstanceUid,
                        "dataClassification" to "PHI",
                        "processingPurpose" to "CLINICAL_PROCESSING",
                    ),
            )
        persistAndAudit(document, actor, "clinical.ingest.dicomweb")
        metricsService.recordClinicalDocument("DICOMWEB")
        dispatch("DICOMWEB_INGESTED", document)
        return document
    }

    fun recent(
        tenantId: String,
        limit: Int = 100,
    ): List<ClinicalDocument> = clinicalDocumentStore.recent(tenantId, limit)

    fun findByPatientId(
        tenantId: String,
        patientId: String,
        limit: Int = 100,
    ): List<ClinicalDocument> = clinicalDocumentStore.findByPatientId(tenantId, patientId, limit)

    private fun persistAndAudit(
        document: ClinicalDocument,
        actor: String,
        action: String,
    ) {
        clinicalDocumentStore.append(document)
        auditTrailService.record(
            AuditEvent(
                tenantId = document.tenantId,
                actor = actor,
                action = action,
                resourceType = "clinical_document",
                resourceId = document.externalId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-005"),
                details = mapOf("documentType" to document.documentType.name),
            ),
        )
    }

    private fun dispatch(
        eventType: String,
        document: ClinicalDocument,
    ) {
        val payload = json.encodeToString(document)
        serverlessDispatchService.enqueue(
            eventType = eventType,
            partitionKey = document.patientId ?: document.externalId ?: "unknown",
            payloadJson = payload,
        )
    }

    private fun requireConsent(
        tenantId: String,
        patientId: String?,
        purpose: String,
    ) {
        if (patientId.isNullOrBlank()) {
            if (!gdprService.hasActiveConsent(tenantId, "UNKNOWN", purpose)) {
                error("GDPR consent check failed: patient reference is required")
            }
            return
        }
        if (!gdprService.hasActiveConsent(tenantId, patientId, purpose)) {
            error("GDPR consent not granted for patient=$patientId purpose=$purpose")
        }
    }
}
