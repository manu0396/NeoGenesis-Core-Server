# Hardware-In-The-Loop Latency Evidence

## Objective
- Demonstrate closed-loop telemetry latency under 50ms on `p95` and `p99`.

## Preconditions
- Real STM32H7 controller connected with production firmware.
- gRPC telemetry stream active with mTLS.
- Server deployed with production-like CPU limits and database.

## Runbook
1. Start telemetry stream from hardware rig.
2. Run synthetic HTTP load to stress decision path:
   - `k6 run perf/k6/telemetry-latency.js -e BASE_URL=http://<host>:8080 -e JWT=<token>`
3. Capture server metrics snapshot:
   - `curl -H "Authorization: Bearer <sre_token>" http://<host>:8080/metrics > perf/hil/metrics-<date>.txt`
4. Export k6 summary:
   - `k6 run --summary-export perf/hil/k6-summary-<date>.json perf/k6/telemetry-latency.js`

## Evidence Pack
- `perf/hil/k6-summary-*.json`
- `perf/hil/metrics-*.txt`
- Oscilloscope/logic-analyzer traces from firmware test bench.
- Signed validation report attached to `docs/regulatory/verification/resilience-report.md`.
