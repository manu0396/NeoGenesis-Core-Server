param(
  [string]$TenantId = "tenant-1",
  [string]$DemoRunId = "seed-sim-run"
)

$ErrorActionPreference = "Stop"

function Get-Token() {
  $body = @{ username = "admin"; password = "admin-password" } | ConvertTo-Json
  $resp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/auth/login" -ContentType "application/json" -Body $body
  return $resp.accessToken
}

$token = Get-Token

$headers = @{
  "Authorization" = "Bearer $token"
  "X-Correlation-Id" = "seed-1"
  "Content-Type" = "application/json"
}

$tenantBody = @{ tenantId = $TenantId; name = "Demo Tenant" } | ConvertTo-Json
try {
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/admin/tenants?tenant_id=$TenantId" -Headers $headers -Body $tenantBody | Out-Null
} catch {
  if ($_.Exception.Response.StatusCode.Value__ -ne 409) { throw }
}

$userBody = @{ username = "demo"; password = "demo-password"; role = "OPERATOR"; tenantId = $TenantId } | ConvertTo-Json
try {
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/admin/users?tenant_id=$TenantId" -Headers $headers -Body $userBody | Out-Null
} catch {
  if ($_.Exception.Response.StatusCode.Value__ -ne 409) { throw }
}

$token | Out-File -FilePath "scripts\.demo_token.txt" -Encoding ascii
Write-Host "Seed complete. Token stored at scripts\.demo_token.txt"
try {
  $simBody =
    @{
      protocolId = "sim-protocol"
      runId = $DemoRunId
      samples = 20
      intervalMs = 1000
    } | ConvertTo-Json
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/demo/simulator/runs?tenant_id=$TenantId" -Headers @{
      "Authorization" = "Bearer $token"
      "X-Correlation-Id" = "seed-protocol"
      "Content-Type" = "application/json"
    } -Body $simBody | Out-Null
  Write-Host "Simulated protocol created via run $DemoRunId."
} catch {
  Write-Warning "Simulator seed run failed; protocol may already exist: $($_.Exception.Message)"
}
