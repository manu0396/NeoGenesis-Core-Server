# DB Migration Failure

## Symptoms
- App fails at startup with migration errors.
- Flyway reports checksum mismatch or failed version.

## Steps
1. Check failed migration version:
```
SELECT * FROM flyway_schema_history WHERE success = false;
```
2. Stop the application.
3. Restore from latest backup if data corruption is suspected.
4. If safe, repair Flyway state:
```
./gradlew flywayRepair
```
5. Re-run migrations:
```
./gradlew flywayMigrate
```

## Rollback
- If the migration is destructive, restore the DB backup taken before the migration.
