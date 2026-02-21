package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.port.ClinicalDocumentStore
import com.neogenesis.server.domain.model.ClinicalDocument
import com.neogenesis.server.domain.model.ClinicalDocumentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FhirCohortAnalyticsServiceTest {

    @Test
    fun `builds demographics and viability by tissue`() {
        val service = FhirCohortAnalyticsService(
            documentStore = FakeClinicalDocumentStore(
                listOf(
                    ClinicalDocument(
                        documentType = ClinicalDocumentType.FHIR,
                        externalId = "f1",
                        patientId = "p1",
                        content = """{"resourceType":"Patient","gender":"female"}""",
                        metadata = mapOf("tissueType" to "retina", "viabilityPrediction" to "0.93")
                    ),
                    ClinicalDocument(
                        documentType = ClinicalDocumentType.FHIR,
                        externalId = "f2",
                        patientId = "p2",
                        content = """{"resourceType":"Patient","gender":"male"}""",
                        metadata = mapOf("tissueType" to "retina", "viabilityPrediction" to "0.89")
                    )
                )
            )
        )

        val demographics = service.getCohortDemographics()
        val viability = service.getViabilityMetricsByTissue()

        assertEquals(1, demographics["female"])
        assertEquals(1, demographics["male"])
        val retina = viability["retina"]
        assertNotNull(retina)
        assertTrue(retina > 0.90 && retina < 0.92)
    }

    private class FakeClinicalDocumentStore(
        private val docs: List<ClinicalDocument>
    ) : ClinicalDocumentStore {
        override fun append(document: ClinicalDocument) = Unit
        override fun recent(limit: Int): List<ClinicalDocument> = docs.take(limit)
        override fun findByPatientId(patientId: String, limit: Int): List<ClinicalDocument> =
            docs.filter { it.patientId == patientId }.take(limit)
    }
}
