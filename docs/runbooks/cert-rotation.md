# Certificate Rotation (Gateway + Server)

## Server mTLS (gRPC)
1. Stage new cert/key files at the configured paths.
2. If hot reload is enabled, wait for reload interval.
3. If hot reload is disabled, restart the server.

## Gateway client cert
1. Place new client cert/key on the gateway host.
2. Restart the gateway service:
```
systemctl restart neogenesis-gateway
```
3. Verify gateway heartbeat and registration logs.

## Notes
- Keep old certs until rotation is verified.
- For air‑gapped environments, transport certs via signed update bundles.
