package com.neogenesis.server.infrastructure.mqtt

import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.infrastructure.config.AppConfig
import com.neogenesis.server.infrastructure.security.MqttTlsSocketFactoryBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.slf4j.LoggerFactory
import java.util.Base64

class TelemetryMqttAdapter(
    private val config: AppConfig.MqttConfig,
    private val mqttTlsConfig: AppConfig.SecurityConfig.MtlsConfig.MqttMtlsConfig,
    private val onTelemetry: (TelemetryState) -> Unit
) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(TelemetryMqttAdapter::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = MqttClient(config.brokerUrl, config.clientId, MemoryPersistence())

    fun connect() {
        if (client.isConnected) {
            return
        }

        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 10
            keepAliveInterval = 20

            if (!config.username.isNullOrBlank()) {
                userName = config.username
            }
            if (!config.password.isNullOrBlank()) {
                password = config.password.toCharArray()
            }
            if (mqttTlsConfig.enabled) {
                socketFactory = MqttTlsSocketFactoryBuilder.build(mqttTlsConfig)
            }
        }

        client.connect(options)
    }

    fun subscribeToAllPrinters() {
        val topic = "${config.topicRoot}/+"
        client.subscribe(topic) { receivedTopic, message ->
            val printerIdFromTopic = receivedTopic.substringAfterLast('/')
            handleTelemetry(printerIdFromTopic, message.payload)
        }
    }

    private fun handleTelemetry(defaultPrinterId: String, payload: ByteArray) {
        runCatching {
            val raw = payload.toString(Charsets.UTF_8)
            val parsed = json.decodeFromString(MqttTelemetryPayload.serializer(), raw)
            val printerId = parsed.printerId.ifBlank { defaultPrinterId }
            val encryptedBytes = parsed.encryptedImageMatrixBase64
                ?.takeIf { it.isNotBlank() }
                ?.let { Base64.getDecoder().decode(it) }
                ?: byteArrayOf()

            TelemetryState(
                printerId = printerId,
                timestampMs = parsed.timestampMs,
                nozzleTempCelsius = parsed.nozzleTempCelsius,
                extrusionPressureKPa = parsed.extrusionPressureKPa,
                cellViabilityIndex = parsed.cellViabilityIndex,
                encryptedImageMatrix = encryptedBytes,
                bioInkViscosityIndex = parsed.bioInkViscosityIndex,
                bioInkPh = parsed.bioInkPh,
                nirIiTempCelsius = parsed.nirIiTempCelsius,
                morphologicalDefectProbability = parsed.morphologicalDefectProbability,
                printJobId = parsed.printJobId,
                tissueType = parsed.tissueType
            )
        }.onSuccess { telemetry ->
            runCatching { onTelemetry(telemetry) }
                .onFailure { error ->
                    logger.error("Failed processing MQTT telemetry: ${error.message}", error)
                }
        }.onFailure { error ->
            logger.warn("Discarded MQTT telemetry payload: ${error.message}")
        }
    }

    override fun close() {
        if (client.isConnected) {
            client.disconnect()
        }
        client.close()
    }
}

@Serializable
private data class MqttTelemetryPayload(
    @SerialName("printer_id")
    val printerId: String = "",
    @SerialName("timestamp_ms")
    val timestampMs: Long,
    @SerialName("nozzle_temp_celsius")
    val nozzleTempCelsius: Float,
    @SerialName("extrusion_pressure_kpa")
    val extrusionPressureKPa: Float,
    @SerialName("cell_viability_index")
    val cellViabilityIndex: Float,
    @SerialName("encrypted_image_matrix_base64")
    val encryptedImageMatrixBase64: String? = null,
    @SerialName("bio_ink_viscosity_index")
    val bioInkViscosityIndex: Float = 0.0f,
    @SerialName("bio_ink_ph")
    val bioInkPh: Float = 7.4f,
    @SerialName("nir_ii_temp_celsius")
    val nirIiTempCelsius: Float = 37.0f,
    @SerialName("morphological_defect_probability")
    val morphologicalDefectProbability: Float = 0.0f,
    @SerialName("print_job_id")
    val printJobId: String = "",
    @SerialName("tissue_type")
    val tissueType: String = "retina"
)
