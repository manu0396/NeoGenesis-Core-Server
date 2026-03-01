# On-Prem Kit (Helm)

The on-prem kit packages NeoGenesis Core Server and an optional PostgreSQL dependency.

## Files
- `deploy/onprem/Chart.yaml`
- `deploy/onprem/values.yaml`
- `deploy/onprem/values.example.yaml`
- `deploy/onprem/secrets.example.yaml`

## Validate Locally
```
helm lint deploy/onprem
helm template neogenesis-core-server deploy/onprem -f deploy/onprem/values.example.yaml
```

## Install (example)
```
kubectl apply -f deploy/onprem/secrets.example.yaml
helm install neogenesis-core-server deploy/onprem -f deploy/onprem/values.example.yaml
```
