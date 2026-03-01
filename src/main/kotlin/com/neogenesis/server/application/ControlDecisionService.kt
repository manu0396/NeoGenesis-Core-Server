package com.neogenesis.server.application

import com.neogenesis.server.domain.model.ControlCommand
import com.neogenesis.server.domain.model.TelemetryState
import com.neogenesis.server.domain.policy.TelemetrySafetyPolicy

class ControlDecisionService(
    private val safetyPolicy: TelemetrySafetyPolicy,
) {
    fun evaluate(tenantId: String, telemetry: TelemetryState): ControlCommand {
        return safetyPolicy.decide(tenantId, telemetry)
    }
}
