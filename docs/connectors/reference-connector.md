# Reference Connector Implementation

The NeoGenesis Connector SDK allows vendors to build resilient drivers for bio-printers and medical devices.

## Driver Interface

A connector must implement the `Driver` interface:

```kotlin
interface Driver {
    val id: String
    val capabilities: Set<Capability>
    val telemetrySchema: TelemetrySchema
    
    suspend fun init(context: DriverContext)
    suspend fun start()
    suspend fun stop()
    suspend fun health(): DriverHealth
    suspend fun readTelemetry(): TelemetryEvent
}
```

## Resilience Features

Enterprise connectors are automatically wrapped in:
- `SandboxedDriver`: Enforces timeouts and prevents resource exhaustion.
- `ResilientDriver`: Provides backpressure and offline buffering via `FileBackedQueue`.

## Example (Simulated)

See `com.neogenesis.gateway.connector.SimulatedConnectorDriver` for a complete reference implementation.
