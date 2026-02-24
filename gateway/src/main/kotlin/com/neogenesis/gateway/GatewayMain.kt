package com.neogenesis.gateway

import java.nio.file.Path
import java.time.Instant

fun main(args: Array<String>) {
    val configPath = System.getenv("GATEWAY_CONFIG")?.trim().orEmpty()
    val config =
        if (configPath.isNotBlank()) {
            GatewayConfig.load(Path.of(configPath))
        } else {
            GatewayConfig.fromEnv()
        }

    if (args.isNotEmpty()) {
        GatewayCli.run(args, config, Instant.now())
        return
    }

    GatewayRuntime(config).startBlocking()
}
