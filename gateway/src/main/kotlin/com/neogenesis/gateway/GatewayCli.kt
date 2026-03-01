package com.neogenesis.gateway

import java.nio.file.Path

object GatewayCli {
    fun run(args: Array<String>, config: GatewayConfig, startedAt: java.time.Instant) {
        if (args.isEmpty()) return
        when (args[0].lowercase()) {
            "status" -> StatusCommand.printStatus(config, startedAt)
            "diagnostics" -> {
                val output = args.getOrNull(1) ?: "gateway-diagnostics.zip"
                val path = Diagnostics.exportBundle(config, Path.of(output))
                println("diagnostics bundle written: ${path.toAbsolutePath()}")
            }
            else -> println("unknown command: ${args[0]}")
        }
    }
}
