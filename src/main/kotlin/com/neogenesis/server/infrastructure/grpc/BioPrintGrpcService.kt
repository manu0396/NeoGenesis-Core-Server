package com.neogenesis.server.infrastructure.grpc

import com.neogenesis.grpc.BioPrintServiceGrpcKt
import com.neogenesis.grpc.KinematicCommand
import com.neogenesis.grpc.PrinterTelemetry
import com.neogenesis.server.application.error.BadRequestException
import com.neogenesis.server.application.error.ConflictException
import com.neogenesis.server.application.error.DependencyUnavailableException
import com.neogenesis.server.application.telemetry.TelemetryProcessingService
import com.neogenesis.server.domain.model.ControlActionType
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.TelemetryState
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BioPrintGrpcService(
    private val telemetryProcessingService: TelemetryProcessingService,
) : BioPrintServiceGrpcKt.BioPrintServiceCoroutineImplBase() {
    override fun streamTelemetryAndControl(requests: Flow<PrinterTelemetry>): Flow<KinematicCommand> {
        return requests.map { telemetry ->
            try {
                val state = telemetry.toDomain()
                val result =
                    telemetryProcessingService.process(
                        telemetry = state,
                        source = "grpc",
                        actor = "grpc-client",
                    )
                result.command.toGrpc(state)
            } catch (error: Throwable) {
                throw error.toGrpcStatusException()
            }
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
            tissueType = getTissueType(),
        )
    }

    private fun ControlCommand.toGrpc(telemetry: TelemetryState): KinematicCommand {
        val targetPressure = telemetry.extrusionPressureKPa + adjustPressure
        val targetTemp =
            telemetry.nozzleTempCelsius +
                when {
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

    private fun Throwable.toGrpcStatusException(): io.grpc.StatusException {
        if (this is StatusException) {
            return this
        }
        val status =
            when (this) {
                is BadRequestException -> Status.INVALID_ARGUMENT.withDescription(code)
                is ConflictException -> Status.ALREADY_EXISTS.withDescription(code)
                is DependencyUnavailableException -> Status.UNAVAILABLE.withDescription(code)
                is IllegalArgumentException -> Status.INVALID_ARGUMENT.withDescription(message ?: "invalid_argument")
                is IllegalStateException -> {
                    val normalized = message?.lowercase().orEmpty()
                    if (normalized.contains("timeout") || normalized.contains("circuit breaker")) {
                        Status.UNAVAILABLE.withDescription("integration_unavailable")
                    } else {
                        Status.FAILED_PRECONDITION.withDescription(message ?: "failed_precondition")
                    }
                }
                else -> Status.INTERNAL.withDescription("internal_server_error")
            }
        return status.withCause(this).asException()
    }
}
