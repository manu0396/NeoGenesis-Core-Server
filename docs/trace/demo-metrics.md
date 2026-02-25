# Demo Metrics (Trace)

The trace module now tracks a simple reproducibility score per run and stores deterministic drift alerts that you can fetch for reporting.

### Score overview
- **Reproducibility score** = `1.0 - (driftAlerts / telemetryPoints) - status penalty` (runs that abort incur a small penalty).
- Score values range `0..1`; higher means the run matched expectations without triggering drift alerts or aborts.
- `/metrics/score` returns the score plus baseline averages computed over the last `baseline_n` runs (default 5).
  Optional query parameters:
  - `baseline_n` (default 5)
  - `metric_key` (default `pressure_kpa`)
  - `z_threshold` (default 3.0)

### Drift alerts
- When the z-score of a metric exceeds `z_threshold`, a `RegenDriftAlert` is inserted (stored in `regen_drift_alerts`).
- Alerts include severity (`warning`/`critical`), metric name, value, and the threshold that was crossed.
- `/metrics/alerts` returns the persisted alerts for a tenant/run.

### Demo flow (three commands)
1. **Start the demo stack** (sets up tenant, user, simulator run, and server). Run:
   ```powershell
   .\scripts\run_demo.ps1
   ```
2. **Fetch the reproducibility score**:
   ```bash
   curl -H "Authorization: Bearer $TOKEN" -H "X-Correlation-Id: demo-1" \
     "http://localhost:8080/metrics/score?tenant_id=tenant-1&run_id=sim-run-1"
   ```
3. **Inspect drift alerts**:
   ```bash
   curl -H "Authorization: Bearer $TOKEN" -H "X-Correlation-Id: demo-2" \
     "http://localhost:8080/metrics/alerts?tenant_id=tenant-1&run_id=sim-run-1"
   ```

Collect the token out of `scripts\.demo_token.txt` (created by `seed_demo_data.ps1`) before issuing the above curl commands.
