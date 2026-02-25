param(
  [string]$TenantId = "tenant-1",
  [string]$RunId = "sim-run-1"
)

$ErrorActionPreference = "Stop"

function Set-EnvFromFile($path) {
  if (-not (Test-Path $path)) {
    throw "Missing env file: $path"
  }
  Get-Content $path | ForEach-Object {
    $line = $_.Trim()
    if ($line.Length -eq 0 -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Length -lt 2) { return }
    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($name.Length -gt 0) { Set-Item -Path "Env:$name" -Value $value }
  }
}

Set-EnvFromFile ".env.example"

$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_USER = "admin"
$env:ADMIN_BOOTSTRAP_PASSWORD = "admin-password"
$env:ADMIN_API_MODE = "true"
$env:EVIDENCE_PACK_MODE = "true"
$env:AUDIT_BUNDLE_MODE = "true"
$env:COMPLIANCE_MODE = "false"

Write-Host "Starting docker compose dependencies..."
docker compose -f docker-compose.local.yml up -d postgres | Out-Host

Write-Host "Starting server (new window)..."
Start-Process -FilePath "powershell" -ArgumentList "-NoExit", "-Command", "cd '$PWD'; ./gradlew run"

Write-Host "Waiting for server health..."
$healthUrl = "http://localhost:8080/health"
for ($i = 0; $i -lt 30; $i++) {
  try {
    $resp = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 2
    if ($resp.StatusCode -eq 200) { break }
  } catch {}
  Start-Sleep -Seconds 2
}

Write-Host "Seeding demo data..."
& scripts\seed_demo_data.ps1 -TenantId $TenantId | Out-Host

$tokenFile = "scripts\.demo_token.txt"
$token = ""
if (Test-Path $tokenFile) {
  $token = Get-Content $tokenFile | Select-Object -First 1
}

Write-Host ""
Write-Host "Run simulator:"
Write-Host "curl -X POST -H `"Authorization: Bearer $token`" -H `"X-Correlation-Id: demo-1`" -H `"Content-Type: application/json`" -d `"{`"protocolId`":`"sim-protocol`",`"runId`":`"$RunId`",`"samples`":120,`"intervalMs`":1000,`"failureAt`":90}`" `"http://localhost:8080/demo/simulator/runs?tenant_id=$TenantId`""
Write-Host ""
Write-Host "Export evidence CSV:"
Write-Host "curl -H `"Authorization: Bearer $token`" -H `"X-Correlation-Id: demo-2`" `"http://localhost:8080/evidence-pack/job/$RunId/report.csv?tenant_id=$TenantId`""
Write-Host ""
Write-Host "Export evidence bundle:"
Write-Host "curl -H `"Authorization: Bearer $token`" -H `"X-Correlation-Id: demo-3`" `"http://localhost:8080/evidence-pack/job/$RunId/bundle.zip?tenant_id=$TenantId`" -o bundle.zip"
