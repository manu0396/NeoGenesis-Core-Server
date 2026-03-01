package com.neogenesis.server.application.retina

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.port.RetinalPlanStore
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.RetinalControlConstraints
import com.neogenesis.server.domain.model.RetinalLayerSpec
import com.neogenesis.server.domain.model.RetinalPrintPlan
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.util.UUID

class RetinalPlanningService(
    private val retinalPlanStore: RetinalPlanStore,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService,
) {
    fun createPlanFromDicom(
        patientId: String,
        sourceDocumentId: String?,
        metadata: Map<String, String>,
        actor: String,
    ): RetinalPrintPlan {
        val layerCount = metadata["layerCount"]?.toIntOrNull()?.coerceIn(4, 14) ?: 10
        val retinalThickness = metadata["retinalThicknessMicrons"]?.toFloatOrNull()?.coerceIn(120.0f, 450.0f) ?: 280.0f
        val targetDensity = metadata["targetCellDensity"]?.toIntOrNull()?.coerceIn(100, 8000) ?: 1200

        val nominalLayerThickness = retinalThickness / layerCount
        val layers =
            (1..layerCount).map { index ->
                RetinalLayerSpec(
                    layerName = retinalLayerName(index),
                    thicknessMicrons = nominalLayerThickness,
                    targetCellDensity = targetDensity,
                    bioInkViscosityIndex = 0.72f + (index * 0.01f),
                )
            }

        val constraints =
            RetinalControlConstraints(
                targetNozzleTempCelsius = metadata["targetNozzleTempCelsius"]?.toFloatOrNull() ?: 36.8f,
                tempToleranceCelsius = metadata["tempToleranceCelsius"]?.toFloatOrNull() ?: 0.8f,
                targetPressureKPa = metadata["targetPressureKPa"]?.toFloatOrNull() ?: 108.0f,
                pressureToleranceKPa = metadata["pressureToleranceKPa"]?.toFloatOrNull() ?: 8.0f,
                minCellViability = metadata["minCellViability"]?.toFloatOrNull() ?: 0.90f,
                maxMorphologicalDefectProbability = metadata["maxMorphologicalDefectProbability"]?.toFloatOrNull() ?: 0.12f,
                maxNirIiTempCelsius = metadata["maxNirIiTempCelsius"]?.toFloatOrNull() ?: 38.5f,
                targetBioInkPh = metadata["targetBioInkPh"]?.toFloatOrNull() ?: 7.35f,
                phTolerance = metadata["phTolerance"]?.toFloatOrNull() ?: 0.12f,
            )

        val plan =
            RetinalPrintPlan(
                planId = "retina-plan-${UUID.randomUUID()}",
                patientId = patientId,
                sourceDocumentId = sourceDocumentId,
                blueprintVersion = metadata["blueprintVersion"] ?: "retina-2027.1",
                layers = layers,
                constraints = constraints,
            )

        retinalPlanStore.save(plan)
        auditTrailService.record(
            AuditEvent(
                actor = actor,
                action = "retina.plan.create",
                resourceType = "retinal_plan",
                resourceId = plan.planId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-002", "REQ-ISO-005"),
                details = mapOf("patientId" to patientId),
            ),
        )
        metricsService.recordRetinaPlanCreated()
        return plan
    }

    fun findByPlanId(planId: String): RetinalPrintPlan? = retinalPlanStore.findByPlanId(planId)

    fun findRecent(limit: Int = 100): List<RetinalPrintPlan> = retinalPlanStore.findRecent(limit)

    fun findLatestByPatientId(patientId: String): RetinalPrintPlan? = retinalPlanStore.findLatestByPatientId(patientId)

    private fun retinalLayerName(index: Int): String {
        return when (index) {
            1 -> "Nerve Fiber Layer"
            2 -> "Ganglion Cell Layer"
            3 -> "Inner Plexiform Layer"
            4 -> "Inner Nuclear Layer"
            5 -> "Outer Plexiform Layer"
            6 -> "Outer Nuclear Layer"
            7 -> "Photoreceptor Layer"
            8 -> "Retinal Pigment Epithelium"
            else -> "Support Layer $index"
        }
    }
}
