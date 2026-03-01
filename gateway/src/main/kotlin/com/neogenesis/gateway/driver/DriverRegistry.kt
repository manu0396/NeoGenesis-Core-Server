package com.neogenesis.gateway.driver

class DriverRegistry {
    fun create(name: String): Driver {
        return when (name.lowercase()) {
            "simulated" -> SimulatedDriver(runId = "run-sim")
            else -> error("Unknown driver: $name")
        }
    }
}
