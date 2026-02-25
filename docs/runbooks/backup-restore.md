# Backup / Restore (PostgreSQL)

## Backup
Prereqs:
- `pg_dump` available
- `DB_URL`, `DB_USER`, `DB_PASSWORD` set

Example:
```
scripts/backup.sh /backups/core-server_$(date +%F).dump
```

## Restore
Example:
```
scripts/restore.sh /backups/core-server_2026-02-25.dump
```

## Notes
- Stop the application before restore.
- Verify DB connectivity after restore and re-run migrations.
