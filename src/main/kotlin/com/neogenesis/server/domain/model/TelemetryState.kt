package com.neogenesis.server.domain.model

data class TelemetryState(
    val printerId: String,
    val timestampMs: Long,
    val nozzleTempCelsius: Float,
    val extrusionPressureKPa: Float,
    val cellViabilityIndex: Float,
    val encryptedImageMatrix: ByteArray = byteArrayOf(),
    val bioInkViscosityIndex: Float = 0.0f,
    val bioInkPh: Float = 7.4f,
    val nirIiTempCelsius: Float = 37.0f,
    val morphologicalDefectProbability: Float = 0.0f,
    val printJobId: String = "",
    val tissueType: String = "retina"
) {
    fun isCriticalViability(): Boolean = cellViabilityIndex < 0.85f
    fun isCriticalTemperature(): Boolean = nozzleTempCelsius < 20.0f || nozzleTempCelsius > 42.0f
    fun isCriticalPressure(): Boolean = extrusionPressureKPa < 30.0f || extrusionPressureKPa > 220.0f
}
