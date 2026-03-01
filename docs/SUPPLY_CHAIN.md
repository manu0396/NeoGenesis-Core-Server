Supply Chain & SBOM

SBOM Generation
- Use CycloneDX Gradle plugin.
- Command:
  - `./gradlew cyclonedxBom`

Output
- `build/reports/bom/sbom.json`

Policy
- Generate SBOM for every release candidate.
- Store SBOM with release artifacts.

Notes
- Paid Docker services are not configured in this repo. Update supply-chain steps if you add Docker SaaS integrations.
