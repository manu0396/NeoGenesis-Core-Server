# 10-Minute Demo Quickstart

This demo pack lets you run the NeoGenesis server, seed tenant/user/protocol data, and exercise the simulator in a single PowerShell command.

## 1. Pre-flight (one-time)
- Install Docker Desktop and ensure `docker compose` is on your path.
- Confirm Java 21/Gradle can run via `./gradlew -v`.
- Optionally open PowerShell and run `Set-ExecutionPolicy Bypass -Scope Process -Force` if scripts are blocked.

## 2. Launch the demo (single command)
```powershell
.\scripts\run_demo.ps1
```

What this command does:
1. Loads `.env.example`, flips on admin/audit/evidence modes, and launches `postgres` via `docker compose`.
2. Starts the server in a new PowerShell window (`./gradlew run`) so you can leave it running.
3. Waits for `http://localhost:8080/health`, seeds `tenant-1` + `demo` user + `sim-protocol` (via `seed_demo_data.ps1`), and captures a demo JWT.
4. Prints ready-to-run `curl` commands for exercising the simulator + exporting evidence, plus a pointer to `docs/demo/quickstart.md`.

## 3. Run the simulator & exports
After the script finishes, copy/paste the `curl` commands it emitted:

- **Simulator run (creates telemetry + evidence):**
  ```powershell
  curl -X POST -H "Authorization: Bearer <token>" -H "X-Correlation-Id: demo-1" -H "Content-Type: application/json" -d '{"protocolId":"sim-protocol","runId":"<run-id>","samples":120,"intervalMs":1000,"failureAt":90}' "http://localhost:8080/demo/simulator/runs?tenant_id=tenant-1"
  ```

- **Export CSV report:**
  ```powershell
  curl -H "Authorization: Bearer <token>" -H "X-Correlation-Id: demo-2" "http://localhost:8080/evidence-pack/job/<run-id>/report.csv?tenant_id=tenant-1" -o demo-report.csv
  ```

- **Export zipped evidence bundle:**
  ```powershell
  curl -H "Authorization: Bearer <token>" -H "X-Correlation-Id: demo-3" "http://localhost:8080/evidence-pack/job/<run-id>/bundle.zip?tenant_id=tenant-1" -o demo-bundle.zip
  ```

Replace `<token>` and `<run-id>` with the values printed by `run_demo.ps1`.

## 4. Review results & clean
- Open `demo-report.csv` or `demo-bundle.zip` to inspect the evidence bundle manifest.
- Logs appear under `build/logs`, and the server window remains open for further exploration.
- To tear down the stack:
  ```powershell
  docker compose -f docker-compose.local.yml down
  ```

## Advanced
- Re-run only the seeds (e.g., to refresh tenant/user/protocol) with:
  ```powershell
  .\scripts\seed_demo_data.ps1 -TenantId tenant-1 -DemoRunId seed-sim-$(Get-Date -Format yyyyMMddHHmm)
  ```
- Swap `TenantId` or run IDs to simulate multi-tenant demos.
