package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.grpc.BioPrintServiceGrpcKt
import com.neogenesis.grpc.KinematicCommand
import com.neogenesis.grpc.PrinterTelemetry
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.TelemetryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BioPrintGrpcService(
    private val telemetryProcessingService: TelemetryProcessingService
) : BioPrintServiceGrpcKt.BioPrintServiceCoroutineImplBase() {

    override fun streamTelemetryAndControl(requests: Flow<PrinterTelemetry>): Flow<KinematicCommand> {
        return requests.map { telemetry ->
            val state = telemetry.toDomain()
            val result = telemetryProcessingService.process(
                telemetry = state,
                source = "grpc",
                actor = "grpc-client"
            )
            result.command.toGrpc(state)
        }
    }

    private fun PrinterTelemetry.toDomain(): TelemetryState {
        return TelemetryState(
            printerId = getPrinterId(),
            timestampMs = getTimestampMs(),
            nozzleTempCelsius = getNozzleTempCelsius(),
            extrusionPressureKPa = getExtrusionPressureKpa(),
            cellViabilityIndex = getCellViabilityIndex(),
            encryptedImageMatrix = getEncryptedImageMatrix().toByteArray(),
            bioInkViscosityIndex = getBioInkViscosityIndex(),
            bioInkPh = getBioInkPh(),
            nirIiTempCelsius = getNirIiTempCelsius(),
            morphologicalDefectProbability = getMorphologicalDefectProbability(),
            printJobId = getPrintJobId(),
            tissueType = getTissueType()
        )
    }

    private fun ControlCommand.toGrpc(telemetry: TelemetryState): KinematicCommand {
        val targetPressure = telemetry.extrusionPressureKPa + adjustPressure
        val targetTemp = telemetry.nozzleTempCelsius + when {
            adjustSpeed > 0f -> 0.4f
            adjustSpeed < 0f -> -0.4f
            else -> 0.0f
        }

        return KinematicCommand.newBuilder()
            .setCommandId(commandId)
            .setActionType(actionType.name)
            .setAdjustPressure(adjustPressure)
            .setAdjustSpeed(adjustSpeed)
            .setTargetNozzleTempCelsius(targetTemp)
            .setTargetPressureKpa(targetPressure)
            .setEmergencyStopLatched(actionType == ControlActionType.EMERGENCY_HALT)
            .build()
    }
}
