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

$dockerAvailable = $null -ne (Get-Command docker -ErrorAction SilentlyContinue)
if ($dockerAvailable) {
  Write-Host "Docker detected. Starting postgres via docker compose..."
  docker compose -f docker-compose.local.yml up -d postgres | Out-Host
  $env:DB_URL = "jdbc:postgresql://localhost:5432/neogenesis"
  $env:DB_USER = "neogenesis"
  $env:DB_PASSWORD = "neogenesis"
} else {
  Write-Host "Docker not detected. Using demo embedded DB (DEMO_DB=true)."
  $env:DEMO_DB = "true"
}

Write-Host "Ensuring server is running..."
$healthUrl = "http://localhost:8080/health"
$serverUp = $false
try {
  $resp = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 2
  if ($resp.StatusCode -eq 200) { $serverUp = $true }
} catch {}

if (-not $serverUp) {
  Write-Host "Starting server (new window)..."
  Start-Process -FilePath "powershell" -ArgumentList "-NoExit", "-Command", "cd '$PWD'; ./gradlew run"
  for ($i = 0; $i -lt 30; $i++) {
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 2
      if ($resp.StatusCode -eq 200) { $serverUp = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
  }
}

if (-not $serverUp) {
  throw "Server did not start within expected time."
}

Write-Host "Seeding demo data..."
& scripts\seed_demo_data.ps1 -TenantId $TenantId | Out-Host

$tokenFile = "scripts\.demo_token.txt"
if (-not (Test-Path $tokenFile)) {
  throw "Missing token file: $tokenFile"
}
$token = Get-Content $tokenFile | Select-Object -First 1

$headers = @{
  "Authorization" = "Bearer $token"
  "X-Correlation-Id" = "deliverables-1"
  "Content-Type" = "application/json"
}

$runBody = @{
  protocolId = "sim-protocol"
  runId = $RunId
  samples = 120
  intervalMs = 1000
  failureAt = 90
} | ConvertTo-Json

Write-Host "Starting simulated run..."
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/demo/simulator/runs?tenant_id=$TenantId" -Headers $headers -Body $runBody | Out-Null

Write-Host "Waiting for run completion..."
$completed = $false
for ($i = 0; $i -lt 20; $i++) {
  $events = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/demo/simulator/runs/$RunId/events?tenant_id=$TenantId&limit=500" -Headers $headers
  if ($events.events | Where-Object { $_.eventType -eq "sim.run.complete" }) {
    $completed = $true
    break
  }
  Start-Sleep -Seconds 2
}

if (-not $completed) {
  Write-Host "Run completion not observed; continuing with export."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path "build/deliverables" $timestamp
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

Write-Host "Downloading deliverables to $outDir..."
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/evidence-pack/job/$RunId/report.csv?tenant_id=$TenantId" -Headers $headers -OutFile (Join-Path $outDir "Sample_Run_Report.csv")
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/audit-bundle/job/$RunId.zip?tenant_id=$TenantId" -Headers $headers -OutFile (Join-Path $outDir "Audit_Bundle.zip")

$manifest =
  @{}
Get-ChildItem $outDir | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
  $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
  $manifest[$_.Name] = $hash
}

$manifestPath = Join-Path $outDir "manifest.json"
$manifest | ConvertTo-Json | Out-File -FilePath $manifestPath -Encoding utf8

Write-Host "Deliverables written to: $outDir"
Write-Host "Sample_Run_Report.csv"
Write-Host "Audit_Bundle.zip"
Write-Host "manifest.json"
