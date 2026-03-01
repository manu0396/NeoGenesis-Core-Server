# Local Dev

Run locally with Postgres:

```
./runLocal.sh
```

With observability stack:

```
./runLocal.sh obs
```

OTEL envs (server):
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317`
- `OTEL_SERVICE_NAME=neogenesis-core-server`
- `LOG_FORMAT=json`
