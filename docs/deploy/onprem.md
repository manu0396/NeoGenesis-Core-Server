# On-Prem Helm Chart

## Chart overview
- `deploy/onprem` ships a minimal Helm chart that deploys the `neoGenesis/core-server` pod, injects Postgres connection settings, and exposes port `8080`.
- Secrets (DB password, admin password) are controlled externally via `secrets.example.yaml`.

## Values and overrides
- `values.yaml` controls image, service port, Postgres host/credentials, and bootstrap admin user.
- Override per environment:
  ```bash
  helm upgrade --install neogenesis deploy/onprem \
    --set image.tag=1.0.0 \
    --set postgres.host=database.internal \
    --set admin.password=$(openssl rand -base64 12)
  ```
- If you want to keep secrets entirely in Kubernetes, apply `deploy/onprem/secrets.example.yaml` (replace `<base64>` with encoded secrets) and reference them via `envFrom` in the deployment (future iteration).

## Local validation
- `helm lint deploy/onprem`
- `helm template deploy/onprem`

## CI checks
The `.github/workflows/helm-onprem.yml` workflow now runs `helm lint` and `helm template` for this chart on every PR.
