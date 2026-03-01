package com.neogenesis.gateway.certification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectorCertificationHarnessTest {
    @Test
    fun computeLatencyStatsReturnsMeanAndP95() {
        val stats = computeLatencyStats(listOf(10, 20, 30, 40, 50))
        assertEquals(30.0, stats.meanMs)
        assertTrue(stats.p95Ms >= 40.0)
    }
}
