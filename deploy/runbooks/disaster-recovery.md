# Disaster Recovery Runbook

## Targets
- `RTO`: 60 minutes.
- `RPO`: 5 minutes for clinical/audit/regulatory data.

## Strategy
- PostgreSQL WAL archiving + point-in-time recovery.
- Nightly encrypted snapshot to secondary region.
- IaC restore path for Kubernetes and secrets backend.

## Backup and Restore Commands

### Create a manual backup:
```bash
export DB_URL="jdbc:postgresql://localhost:5432/neogenesis"
export DB_USER="neogenesis"
export DB_PASSWORD="your-password"
./scripts/backup.sh /backups/manual-snapshot-$(date +%Y%m%d).dump
```

### Restore from backup:
```bash
./scripts/restore.sh /backups/manual-snapshot-20260228.dump
```

## Regional failover
1. Declare incident and freeze writes on primary.
2. Promote secondary PostgreSQL from latest WAL.
3. Restore `neogenesis-core` stack in DR cluster.
4. Rotate integration endpoints (PACS, HL7, DIMSE, MQTT broker).
5. Validate:
   - `/health/ready`
   - `/compliance/audit/verify-chain`
   - `/gdpr/erasures`
   - sample telemetry closed-loop path.
6. Record incident evidence and CAPA.
