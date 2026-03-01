# Runbook Despliegue en DOKS (NeoGenesis Core Server v1.0.0)

Este documento define los pasos para desplegar NeoGenesis Core Server en DigitalOcean Kubernetes (DOKS) y dejar listo el entorno para ejecutar la suite de evidencias de produccion.

## Alcance

- Provision de cluster DOKS.
- PostgreSQL gestionado en DigitalOcean.
- Ingress HTTPS para API.
- Deploy de manifests del repo.
- Configuracion de secretos requeridos por workflows GitHub Actions.
- Ejecucion de `Production Evidence Suite`.

## Prerrequisitos

- Cuenta DigitalOcean activa.
- Dominio gestionado (en DO o proveedor externo).
- Repo con acceso GitHub (`manu0396/NeoGenesis-Core-Server`).
- Herramientas en Windows PowerShell:
  - `doctl`
  - `kubectl`
  - `helm`
  - `gh`
  - `python`
  - `openssl` (por ejemplo desde Git for Windows)

## 0) Variables base (PowerShell)

```powershell
$env:NS = "neogenesis"
$env:DOKS_CLUSTER = "neogenesis-prod"
$env:DO_REGION = "ams3"
$env:IMAGE_TAG = "1.0.0"

# Dominio publico de la API
$env:DOMAIN_ZONE = "neogenesis.com"
$env:API_HOST = "api.neogenesis.com"
```

## 1) Login en DigitalOcean y creacion de cluster

```powershell
doctl auth init
doctl account get
```

```powershell
doctl kubernetes cluster create $env:DOKS_CLUSTER `
  --region $env:DO_REGION `
  --version latest `
  --size s-2vcpu-4gb `
  --count 3 `
  --wait
```

```powershell
doctl kubernetes cluster kubeconfig save $env:DOKS_CLUSTER --expiry-seconds 0
kubectl config current-context
kubectl get nodes
```

## 2) Instalar componentes base del cluster

### 2.1 Ingress NGINX

```powershell
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
  --namespace ingress-nginx --create-namespace
```

### 2.2 Cert Manager

```powershell
helm repo add jetstack https://charts.jetstack.io
helm repo update
helm upgrade --install cert-manager jetstack/cert-manager `
  --namespace cert-manager --create-namespace `
  --set crds.enabled=true
```

### 2.3 Argo Rollouts Controller

```powershell
kubectl create namespace argo-rollouts --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
```

## 3) PostgreSQL gestionado (DigitalOcean Managed DB)

Crear DB:

```powershell
doctl databases create neogenesis-pg `
  --engine pg `
  --version 16 `
  --region $env:DO_REGION `
  --size db-s-1vcpu-1gb `
  --num-nodes 1
```

Consultar datos de conexion:

```powershell
doctl databases list
doctl databases connection neogenesis-pg
```

Rellena estas variables con valores reales:

```powershell
$env:DB_HOST = "<DB_HOST>"
$env:DB_PORT = "25060"
$env:DB_NAME = "defaultdb"
$env:DB_USER = "<DB_USER>"
$env:DB_PASS = "<DB_PASS>"
```

JDBC para la app:

```powershell
$env:DB_JDBC_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)?sslmode=require"
```

## 4) Generar secretos criptograficos de app

```powershell
$rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
$bytes48  = New-Object byte[] 48
$bytes32a = New-Object byte[] 32
$bytes32b = New-Object byte[] 32
$rng.GetBytes($bytes48)
$rng.GetBytes($bytes32a)
$rng.GetBytes($bytes32b)
$rng.Dispose()

$env:JWT_SECRET  = [Convert]::ToBase64String($bytes48)
$env:PHI_KEY_B64 = [Convert]::ToBase64String($bytes32a)
$env:PII_KEY_B64 = [Convert]::ToBase64String($bytes32b)
```

## 5) Crear secrets Kubernetes

```powershell
kubectl create namespace $env:NS --dry-run=client -o yaml | kubectl apply -f -

kubectl -n $env:NS create secret generic neogenesis-db `
  --from-literal=jdbc-url="$env:DB_JDBC_URL" `
  --from-literal=username="$env:DB_USER" `
  --from-literal=password="$env:DB_PASS" `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n $env:NS create secret generic neogenesis-auth `
  --from-literal=jwt-secret="$env:JWT_SECRET" `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n $env:NS create secret generic neogenesis-encryption `
  --from-literal=phi-key-b64="$env:PHI_KEY_B64" `
  --from-literal=pii-key-b64="$env:PII_KEY_B64" `
  --dry-run=client -o yaml | kubectl apply -f -

kubectl -n $env:NS create secret generic neogenesis-serverless `
  --from-literal=eventbridge-bus="neogenesis-core-events" `
  --dry-run=client -o yaml | kubectl apply -f -
```

## 6) gRPC mTLS (secreto `neogenesis-grpc-tls`)

```powershell
$openssl = "C:\Program Files\Git\usr\bin\openssl.exe"
& $openssl req -x509 -newkey rsa:4096 -sha256 -days 365 -nodes `
  -keyout grpc.key -out grpc.crt -subj "/CN=neogenesis-grpc"

kubectl -n $env:NS create secret generic neogenesis-grpc-tls `
  --from-file=tls.crt=grpc.crt `
  --from-file=tls.key=grpc.key `
  --from-file=ca.crt=grpc.crt `
  --dry-run=client -o yaml | kubectl apply -f -
```

## 7) Deploy de la aplicacion y rollout

```powershell
kubectl -n $env:NS apply -f deploy/k8s/blue-green/service-blue.yaml
kubectl -n $env:NS apply -f deploy/k8s/blue-green/service-green.yaml
kubectl -n $env:NS apply -f deploy/k8s/base/deployment.yaml
kubectl -n $env:NS apply -f deploy/k8s/canary/analysis-templates.yaml
kubectl -n $env:NS apply -f deploy/k8s/canary/rollout.yaml
```

## 8) Ingress para `neogenesis-core-ingress` (requerido por rollout)

Crear manifiesto:

```powershell
@"
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: neogenesis-core-ingress
  namespace: $($env:NS)
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - $($env:API_HOST)
      secretName: neogenesis-http-tls
  rules:
    - host: $($env:API_HOST)
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: neogenesis-core-blue
                port:
                  number: 80
"@ | Set-Content -Path .\deploy\k8s\base\ingress.generated.yaml -Encoding UTF8
```

Aplicar:

```powershell
kubectl apply -f .\deploy\k8s\base\ingress.generated.yaml
```

## 9) DNS del API host

Obtener IP del Load Balancer de ingress-nginx:

```powershell
kubectl -n ingress-nginx get svc ingress-nginx-controller -o wide
```

Si usas DNS de DigitalOcean, crear registro A:

```powershell
# Ajusta el valor de IP resultante
$env:INGRESS_IP = "<INGRESS_PUBLIC_IP>"
doctl compute domain records create $env:DOMAIN_ZONE `
  --record-type A `
  --record-name "api" `
  --record-data $env:INGRESS_IP `
  --record-ttl 60
```

Validar resolucion:

```powershell
nslookup $env:API_HOST
```

## 10) Verificaciones runtime

```powershell
kubectl -n $env:NS get pods
kubectl -n $env:NS get rollout
kubectl -n $env:NS get ingress
```

```powershell
curl "https://$($env:API_HOST)/health/live"
curl "https://$($env:API_HOST)/health/ready"
```

## 11) Configurar secrets de GitHub Actions (suite de evidencias)

Prepara variables:

```powershell
$env:KUBECONFIG_B64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.kube\config"))

# URLs usadas por release-readiness (deben ser accesibles por el runner)
$env:READINESS_DB_URL = "postgresql://$($env:DB_USER):$($env:DB_PASS)@$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)?sslmode=require"
$env:READINESS_DB_ADMIN_URL = "postgresql://$($env:DB_USER):$($env:DB_PASS)@$($env:DB_HOST):$($env:DB_PORT)/postgres?sslmode=require"

# Alertmanager publico o accesible por runner
$env:ALERTMANAGER_URL = "https://alertmanager.neogenesis.com"
$env:ALERTMANAGER_AUTH_HEADER = "Authorization: Bearer <TOKEN>"  # opcional
```

Generar JWT para workflows (`RELEASE_READINESS_JWT`, `NEOGENESIS_PERF_JWT`):

```powershell
@'
import base64, json, hmac, hashlib, time, os

secret = os.environ["JWT_SECRET"].encode()

def b64url(x): return base64.urlsafe_b64encode(x).rstrip(b'=')

def issue(roles):
    header = {"alg":"HS256","typ":"JWT"}
    payload = {
        "sub":"github-actions",
        "iss":"neogenesis-auth",
        "aud":"neogenesis-api",
        "roles":roles,
        "iat":int(time.time()),
        "exp":int(time.time())+3600
    }
    h = b64url(json.dumps(header,separators=(',',':')).encode())
    p = b64url(json.dumps(payload,separators=(',',':')).encode())
    s = h+b'.'+p
    sig = b64url(hmac.new(secret, s, hashlib.sha256).digest())
    return (s+b'.'+sig).decode()

print("READINESS=" + issue(["sre","auditor"]))
print("PERF=" + issue(["controller"]))
'@ | python -
```

Subir secrets al repo:

```powershell
gh repo set-default manu0396/NeoGenesis-Core-Server

gh secret set KUBECONFIG_B64 --body "$env:KUBECONFIG_B64"
gh secret set READINESS_DB_URL --body "$env:READINESS_DB_URL"
gh secret set READINESS_DB_ADMIN_URL --body "$env:READINESS_DB_ADMIN_URL"
gh secret set ALERTMANAGER_URL --body "$env:ALERTMANAGER_URL"
gh secret set ALERTMANAGER_AUTH_HEADER --body "$env:ALERTMANAGER_AUTH_HEADER"

# Sustituye por valores impresos por el script Python anterior
gh secret set RELEASE_READINESS_JWT --body "<READINESS_TOKEN>"
gh secret set NEOGENESIS_PERF_JWT --body "<PERF_TOKEN>"
```

## 12) Ejecutar suite de evidencias de produccion

```powershell
gh workflow run "Production Evidence Suite" `
  -f base_url="https://$($env:API_HOST)" `
  -f metrics_path="/metrics" `
  -f k8s_namespace="$env:NS" `
  -f image_tag="$env:IMAGE_TAG" `
  -f perf_threshold_p95_ms="50" `
  -f perf_threshold_p99_ms="75" `
  -f rollout_name="neogenesis-core"
```

```powershell
$runId = gh run list --workflow "Production Evidence Suite" --limit 1 --json databaseId -q '.[0].databaseId'
gh run watch $runId --exit-status
gh run download $runId -D .\production-evidence-artifacts
```

## 13) Cierre GO/NO-GO

Con artifacts descargados:

- `release-readiness-evidence-*`
- `k6-latency-evidence`
- `canary-rollout-evidence-*`

Actualiza:

- `docs/production-go-no-go-checklist.md`

Si los P0 quedan en verde con evidencia real, estado final `GO`.

## Notas operativas importantes

- No usar placeholders (`<...>`) en comandos finales.
- No subir secretos a git ni al chat.
- Rotar inmediatamente cualquier secreto expuesto historicamente.
- `deploy/k8s/base/secrets.yaml` no debe utilizarse como fuente de produccion; crear secretos por `kubectl create secret ...` como en este runbook.
