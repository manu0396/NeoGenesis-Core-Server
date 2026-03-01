# Air-Gapped Deployment Bundle

NeoGenesis Enterprise Pack v1.0.0 supports fully air-gapped deployments via signed artifact bundles.

## Bundle Creation

The `generate_deliverables.ps1` script creates a self-contained tarball including:
- Core Server Docker Images (exported via `docker save`).
- Helm Charts.
- Database Schema Migrations.
- Validation Templates (IQ/OQ).
- SBOM (Software Bill of Materials).

## Signing and Verification

Each bundle is signed using `cosign` or a similar tool.

### To verify a bundle:
1. Import the NeoGenesis Public Key.
2. Run the verification command:
   ```bash
   ./scripts/verify-bundle.sh neogenesis-v1.0.0.tar.gz
   ```

## Local Registry Loading

In the air-gapped environment:
1. Load images into the local registry:
   ```bash
   docker load -i images/core-server.tar
   docker push my-local-registry:5000/neogenesis-core-server:1.0.0
   ```
2. Deploy using Helm pointing to the local registry.
