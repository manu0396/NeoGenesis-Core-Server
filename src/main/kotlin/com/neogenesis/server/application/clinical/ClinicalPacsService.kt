package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.error.DependencyUnavailableException
import com.neogenesis.server.application.resilience.IntegrationResilienceExecutor
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.infrastructure.clinical.DicomWebClient
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService

class ClinicalPacsService(
    private val dicomWebClient: DicomWebClient?,
    private val clinicalIntegrationService: ClinicalIntegrationService,
    private val metricsService: OperationalMetricsService,
    private val resilienceExecutor: IntegrationResilienceExecutor,
) {
    fun importLatestStudy(
        tenantId: String,
        patientId: String,
        actor: String,
    ): ClinicalDocument? {
        val client =
            dicomWebClient
                ?: throw DependencyUnavailableException("pacs_integration_disabled", "PACS/DICOMweb integration is not enabled")
        return resilienceExecutor.execute("dicomweb", "importLatestStudy") {
            runCatching {
                val latest = client.fetchLatestStudyMetadata(patientId) ?: return@runCatching null
                clinicalIntegrationService.ingestDicomWebMetadata(
                    tenantId = tenantId,
                    patientId = patientId,
                    studyInstanceUid = latest.studyInstanceUid,
                    metadataJson = latest.metadataJson,
                    actor = actor,
                ).also {
                    metricsService.recordPacsFetch("success")
                }
            }.getOrElse {
                metricsService.recordPacsFetch("error")
                throw it
            }
        }
    }

    fun queryStudies(
        patientId: String,
        limit: Int,
    ): String {
        val client =
            dicomWebClient
                ?: throw DependencyUnavailableException("pacs_integration_disabled", "PACS/DICOMweb integration is not enabled")
        return resilienceExecutor.execute("dicomweb", "queryStudies") {
            runCatching {
                client.queryStudies(patientId = patientId, limit = limit).also {
                    metricsService.recordPacsFetch("success")
                }
            }.getOrElse {
                metricsService.recordPacsFetch("error")
                throw it
            }
        }
    }
}
