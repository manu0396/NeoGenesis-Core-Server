# Gateway Offline Backlog

Gateways use a local queue when offline. When connectivity returns, drain the backlog.

## Steps
1. Verify gateway health:
```
./gradlew :gateway:run --args="status"
```
2. Check queue directory on the gateway host:
- Default: `gateway-data/queue`
3. Restart gateway to trigger drain:
```
systemctl restart neogenesis-gateway
```
4. Monitor server ingest and queue size decrease.

## Manual drain (if needed)
Use the gateway log to confirm enqueue/dequeue progress. If the backlog grows, reduce telemetry rate or increase queue max bytes in `gateway.env`.
