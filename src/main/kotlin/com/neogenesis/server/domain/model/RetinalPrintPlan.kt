package com.neogenesis.server.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RetinalLayerSpec(
    val layerName: String,
    val thicknessMicrons: Float,
    val targetCellDensity: Int,
    val bioInkViscosityIndex: Float,
)

@Serializable
data class RetinalControlConstraints(
    val targetNozzleTempCelsius: Float,
    val tempToleranceCelsius: Float,
    val targetPressureKPa: Float,
    val pressureToleranceKPa: Float,
    val minCellViability: Float,
    val maxMorphologicalDefectProbability: Float,
    val maxNirIiTempCelsius: Float,
    val targetBioInkPh: Float,
    val phTolerance: Float,
)

@Serializable
data class RetinalPrintPlan(
    val planId: String,
    val patientId: String,
    val sourceDocumentId: String?,
    val blueprintVersion: String,
    val layers: List<RetinalLayerSpec>,
    val constraints: RetinalControlConstraints,
    val createdAtMs: Long = System.currentTimeMillis(),
)
