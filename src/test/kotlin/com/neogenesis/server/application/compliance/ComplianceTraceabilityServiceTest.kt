package com.neogenesis.server.application.compliance

import kotlin.test.Test
import kotlin.test.assertTrue

class ComplianceTraceabilityServiceTest {
    @Test
    fun `loads traceability requirements from classpath`() {
        val service = ComplianceTraceabilityService.fromClasspath()

        assertTrue(service.allRequirements().isNotEmpty())
        assertTrue(service.operationCoverage().containsKey("telemetry.process"))
    }
}
