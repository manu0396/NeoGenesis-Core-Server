# Air‑gapped Updates (Signed Bundle Placeholder)

Air‑gapped installs require signed update bundles and offline verification.

## Bundle Layout (placeholder)
- `core-server-image.tar` (container image)
- `gateway-image.tar` (optional)
- `values.yaml` (deployment config)
- `secrets.yaml` (sealed or externally supplied)
- `SIGNATURE.txt` (detached signature)
- `SHA256SUMS` (checksums)

## Verify
1. Validate signatures with the offline public key.
2. Verify checksums match `SHA256SUMS`.
3. Load images:
```
docker load -i core-server-image.tar
```
4. Deploy via Helm or local manifests.

## Notes
- Store public keys in offline HSM if available.
- Document the signing process in internal security SOPs.
