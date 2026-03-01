# Runbook: Gateway Offline Backlog

## Symptoms
- Gateway telemetry/events delayed
- Queue size growing on gateway disk

## Immediate Actions
1. Check gateway logs for upload failures.
2. Verify network connectivity to server.
3. Verify gateway credentials and mTLS cert validity.

## Recovery
- Restart gateway to re-establish gRPC channel.
- If backlog exceeds disk limits, increase `QUEUE_MAX_BYTES` and restart.
- Ensure server accepts the gateway (registration/heartbeat success).

## Validation
- Queue size decreases
- `pushTelemetry` and `pushRunEvents` acks succeed
