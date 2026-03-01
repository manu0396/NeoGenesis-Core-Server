package com.neogenesis.server.application.clinical

import com.neogenesis.server.application.resilience.IntegrationResilienceExecutor
import com.neogenesis.server.infrastructure.clinical.DimseCommandClient

class ClinicalDimseService(
    private val dimseClient: DimseCommandClient?,
    private val resilienceExecutor: IntegrationResilienceExecutor
) {
    fun cFind(queryKeys: Map<String, String>, returnKeys: List<String>): String {
        val client = requireNotNull(dimseClient) { "DIMSE integration is disabled" }
        return resilienceExecutor.execute("dimse", "cfind") {
            client.cFind(queryKeys, returnKeys)
        }
    }

    fun cMove(studyInstanceUid: String, destinationAeTitle: String): String {
        val client = requireNotNull(dimseClient) { "DIMSE integration is disabled" }
        return resilienceExecutor.execute("dimse", "cmove") {
            client.cMove(studyInstanceUid, destinationAeTitle)
        }
    }

    fun cGet(studyInstanceUid: String): String {
        val client = requireNotNull(dimseClient) { "DIMSE integration is disabled" }
        return resilienceExecutor.execute("dimse", "cget") {
            client.cGet(studyInstanceUid)
        }
    }
}
