package com.neogenesis.gateway

import java.time.Instant

class HealthCommand(
    private val config: GatewayConfig,
    private val startedAt: Instant,
) {
    fun startAsync() {
        val thread =
            Thread {
                while (true) {
                    Thread.sleep(10_000)
                    val uptimeSeconds = (System.currentTimeMillis() - startedAt.toEpochMilli()) / 1000
                    println("health: status=ok uptime=${uptimeSeconds}s gatewayId=${config.gatewayId}")
                }
            }
        thread.isDaemon = true
        thread.name = "gateway-health"
        thread.start()
    }
}
