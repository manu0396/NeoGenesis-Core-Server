# Demo Pack Quickstart (10 minutes)

This demo runs end-to-end on Windows with one command.

## Prereqs
- Docker Desktop
- JDK 21
- Node 20/22 (optional, for admin web)

## One-command Demo
From repo root:
```
scripts\run_demo.ps1
```

The script will:
1. Start PostgreSQL via Docker Compose.
2. Start the server in a new PowerShell window.
3. Seed demo data (tenant + demo user).
4. Print curl commands to run the simulator and export evidence.

## Notes
- Uses env vars from `.env.example`.
- Admin bootstrap is enabled for the demo.
- Demo user credentials: `demo` / `demo-password`.
