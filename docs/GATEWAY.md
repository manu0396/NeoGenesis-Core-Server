# Device Gateway

## Run locally

```
./gateway/run-local.sh
```

## Configure

- Copy `gateway/config/gateway.env.example` to `/etc/neogenesis/gateway.env` on Linux and edit values.
- For mTLS, set:
  - `GATEWAY_MTLS=true`
  - `GATEWAY_CLIENT_CERT=/etc/neogenesis/certs/client.crt`
  - `GATEWAY_CLIENT_KEY=/etc/neogenesis/certs/client.key`
  - `SERVER_CA_CERT=/etc/neogenesis/certs/ca.crt`

## Registration + heartbeat

On startup the gateway registers and then heartbeats every `HEARTBEAT_INTERVAL_MS`.

## Systemd

```
cp gateway/systemd/neogenesis-gateway.service /etc/systemd/system/
cp gateway/config/gateway.env.example /etc/neogenesis/gateway.env
systemctl daemon-reload
systemctl enable neogenesis-gateway
systemctl start neogenesis-gateway
```

## Diagnostics bundle

```
./gradlew :gateway:run --args="diagnostics /tmp/gateway-diagnostics.zip"
```

## Status

```
./gradlew :gateway:run --args="status"
```

## Health

The gateway logs a health line every 10s:

```
health: status=ok uptime=120s gatewayId=gw-local-1
```
