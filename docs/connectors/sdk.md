# Connector SDK (Gateway)

This SDK defines the stable interface that connector drivers must implement.
It is **feature-flagged** and safe to ship in 1.0.0.

## Driver contract
- Implement `Driver` from `com.neogenesis.gateway.connector`.
- Lifecycle: `init` → `start` → `readTelemetry` → `stop`.
- Provide `capabilities` and `telemetrySchema`.

## Minimal driver skeleton
```kotlin
class MyDriver : Driver {
    override val id = "my-driver"
    override val capabilities = setOf(Capability.Telemetry)
    override val telemetrySchema =
        TelemetrySchema(
            name = "my-schema",
            version = "1.0",
            fields = listOf(TelemetryField("value", "int", "unit")),
        )

    override suspend fun init(context: DriverContext) {}
    override suspend fun start() {}
    override suspend fun stop() {}
    override suspend fun health() = DriverHealth("ok")
    override suspend fun readTelemetry() =
        TelemetryEvent(Instant.now(), mapOf("value" to "42"))
}
```

## Timeouts
Wrap drivers with `SandboxedDriver(timeoutMs)` to enforce strict execution bounds.

## Configuration
Drivers receive:
- `tenantId`
- `correlationId`
- `config` (key/value map)

## Feature flag
Connector certification endpoints are guarded by:
- `CONNECTOR_CERTIFICATION_MODE=false` (default)
