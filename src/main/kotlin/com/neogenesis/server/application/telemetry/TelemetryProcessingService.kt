package com.neogenesis.server.application.telemetry

import com.neogenesis.server.application.AuditTrailService
import com.neogenesis.server.application.TelemetrySnapshotService
import com.neogenesis.server.application.port.ControlCommandStore
import com.neogenesis.server.application.port.TelemetryEventStore
import com.neogenesis.server.application.sre.LatencyBudgetService
import com.neogenesis.server.application.twin.DigitalTwinService
import com.neogenesis.server.domain.model.AuditEvent
import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.ControlCommandEvent
import com.neogenesis.server.domain.model.DigitalTwinState
import com.neogenesis.server.domain.model.TelemetryEvent
import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.infrastructure.observability.OperationalMetricsService
import java.util.UUID

data class TelemetryProcessingResult(
    val command: ControlCommand,
    val digitalTwinState: DigitalTwinState,
)

class TelemetryProcessingService(
    private val closedLoopControlService: ClosedLoopControlService,
    private val advancedBioSimulationService: AdvancedBioSimulationService,
    private val telemetrySnapshotService: TelemetrySnapshotService,
    private val telemetryEventStore: TelemetryEventStore,
    private val controlCommandStore: ControlCommandStore,
    private val digitalTwinService: DigitalTwinService,
    private val auditTrailService: AuditTrailService,
    private val metricsService: OperationalMetricsService,
    private val latencyBudgetService: LatencyBudgetService,
) {
    fun process(
        tenantId: String,
        telemetry: TelemetryState,
        source: String,
        actor: String,
    ): TelemetryProcessingResult {
        val startedNanos = System.nanoTime()

        telemetrySnapshotService.update(tenantId, telemetry)
        telemetryEventStore.append(
            TelemetryEvent(
                tenantId = tenantId,
                telemetry = telemetry,
                source = source,
            ),
        )
        metricsService.recordTelemetryIngest(source)

        val baselineCommand = closedLoopControlService.decide(tenantId, telemetry)
        val simulation = advancedBioSimulationService.simulate(telemetry)
        val command =
            if (
                simulation.predictedViability < 0.82f &&
                baselineCommand.actionType != com.neogenesis.server.domain.model.ControlActionType.EMERGENCY_HALT
            ) {
                ControlCommand(
                    tenantId = tenantId,
                    commandId = "cmd-${UUID.randomUUID()}",
                    printerId = telemetry.printerId,
                    actionType = com.neogenesis.server.domain.model.ControlActionType.EMERGENCY_HALT,
                    reason = "Advanced simulation predicted viability collapse (${simulation.predictedViability})",
                )
            } else {
                baselineCommand
            }
        controlCommandStore.append(ControlCommandEvent(command = command))
        metricsService.recordControlDecision(command.actionType.name)

        val twinState =
            digitalTwinService.updateFromTelemetry(
                tenantId = tenantId,
                telemetry = telemetry,
                command = command,
                simulatedViability = simulation.predictedViability,
                simulatedShearStressKPa = simulation.shearStressKPa,
            )

        auditTrailService.record(
            AuditEvent(
                tenantId = tenantId,
                actor = actor,
                action = "telemetry.process",
                resourceType = "printer",
                resourceId = telemetry.printerId,
                outcome = "success",
                requirementIds = listOf("REQ-ISO-002", "REQ-ISO-004", "REQ-ISO-005"),
                details =
                    mapOf(
                        "source" to source,
                        "actionType" to command.actionType.name,
                        "simulatedShearStressKPa" to simulation.shearStressKPa.toString(),
                        "simulatedPredictedViability" to simulation.predictedViability.toString(),
                    ),
            ),
        )

        val durationNanos = System.nanoTime() - startedNanos
        metricsService.recordProcessingLatency(durationNanos)
        latencyBudgetService.recordIfBreached(
            tenantId = tenantId,
            printerId = telemetry.printerId,
            source = source,
            durationNanos = durationNanos,
        )

        return TelemetryProcessingResult(
            command = command,
            digitalTwinState = twinState,
        )
    }
}
