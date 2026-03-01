# RegenOps gRPC API Compatibility

The core server currently supports two RegenOps gRPC APIs in parallel:

- Android legacy API: `com.neogenesis.grpc.*` (defined in `contracts/src/main/proto/bioprint.proto`)
- Desktop v1 API: `com.neogenesis.platform.proto.v1.*` (defined in `contracts/src/main/proto/regenops.proto` + `contracts/src/main/proto/platform.proto`)

## Why both exist
The Android KMP client still targets the legacy package while the Desktop KMP client uses the v1 package. The server bridges both APIs to the same RegenOps service/store so that both clients can operate concurrently during migration.

## Notes
- v1 `created_at` fields are ISO-8601 strings (required by Desktop client parsing).
- v1 `version_id` is a string and is mapped to the integer protocol version used internally.
- v1 `TelemetryFrame` is populated from RegenOps telemetry points by matching `metric_key` values.

## Future work
Once Android clients migrate to v1, the legacy API can be deprecated and removed.

## Developer Notes

- Regenerate proto stubs: `./gradlew.bat regenContracts`
- Dual API support: legacy Android uses `com.neogenesis.grpc.*`, desktop v1 uses `com.neogenesis.platform.proto.v1.*`
- Telemetry mapping: update `src/main/kotlin/com/neogenesis/server/infrastructure/grpc/regenops/RegenOpsV1GrpcServices.kt` in `toV1Telemetry()` (metric key -> `TelemetryFrame` fields). Add new keys to `normalizeMetricKey` handling and extend `src/test/kotlin/com/neogenesis/server/infrastructure/grpc/RegenOpsV1GrpcIntegrationTest.kt` with mapping assertions.
