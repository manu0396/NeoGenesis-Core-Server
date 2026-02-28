# Enterprise Validation Plan (IQ/OQ/PQ)

This document outlines the validation strategy for the NeoGenesis Core Server in GxP environments.

## Installation Qualification (IQ)

- **Verification of components**: All microservices (Core, Gateway, Redis, Postgres) are present.
- **Environment hardening**: TLS 1.3 enforced, RLS enabled in Postgres.
- **Dependency Scan**: SBOM generated and verified against allowed licenses.

## Operational Qualification (OQ)

- **Multi-tenancy isolation**: Verified that Tenant A cannot see Tenant B data via RLS.
- **Audit trail integrity**: Tamper-evident hash chain verified with intentional corruption test.
- **E-Signature flow**: Verified that protocol publishing requires dual-control + e-signature.

## Performance Qualification (PQ)

- **Latency Budget**: Verified that processing latency stays within 100ms for 99% of requests.
- **Throughput**: Verified 1000 events/sec per tenant.
