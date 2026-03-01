package com.neogenesis.gateway

import java.time.Instant

object StatusCommand {
    fun printStatus(config: GatewayConfig, startedAt: Instant) {
        val uptimeSeconds = (System.currentTimeMillis() - startedAt.toEpochMilli()) / 1000
        println("status: gatewayId=${config.gatewayId} tenantId=${config.tenantId} uptime=${uptimeSeconds}s")
    }
}
