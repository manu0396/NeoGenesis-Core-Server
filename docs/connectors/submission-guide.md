# Connector Submission Guide

To join the NeoGenesis ecosystem, your connector must pass the Certification Harness.

## Certification Steps

1. **Develop** your driver implementing the `Driver` interface.
2. **Run the Harness** locally:
   ```bash
   ./gradlew :gateway:runCertification --args="--driver=your-driver-id --events=500 --dropRate=0.05"
   ```
3. **Review the Report** in `build/reports/connector-certification/certification-report.md`.
4. **Fix Issues** if the status is `fail` (e.g., high drop rate or latency).

## Quality Gates

- **Drop Rate**: Must be < 10% under simulated instability.
- **Latency (P95)**: Must be < 500ms.
- **Resilience**: Must successfully reconnect and drain buffered events.

## Submission

Submit your `certification-report.json` along with your JAR/Artifact to the NeoGenesis Developer Portal.
