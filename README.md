# Digital Wallet & Double-Entry Ledger Engine

A wallet backend where every money movement is a balanced pair of debit and credit ledger entries, and balances are derived from the ledger, never written directly. The goal is to show correctness, auditability and safe concurrency for money-shaped data.

> 🚧 Work in progress. This README is completed in Phase 2, Step 14.

- `backend/`: Java 21, Spring Boot 3, PostgreSQL 16, Flyway
- `web/`: Next.js frontend (later)
- `docs/`: [ADRs](docs/decisions.md), [learning log](docs/learning-log.md)

## Running locally

Prerequisites: JDK 21 and Docker.

```bash
docker compose up -d                 # Postgres 16 with wallet_owner / wallet_app roles
cd backend
./mvnw spring-boot:run               # dev profile; Flyway migrates on startup
./mvnw verify                        # unit + integration tests (Testcontainers, needs Docker)
```

Dev defaults are in [.env.example](.env.example). Copy it to `.env` to override them.

If you edit a migration that already ran locally, reset the dev database with `docker compose down -v`. Never do this to a shared or production DB; fix forward with a new migration instead.

## Dev seed users

Created on startup in the `dev` profile only (`DevDataSeeder`). **Never use these outside local dev.**

| Email | Password | Role |
|---|---|---|
| admin@wallet.local | admin-pass-123 | ADMIN |
| support@wallet.local | support-pass-123 | SUPPORT |
| alice@wallet.local | alice-pass-123 | CUSTOMER |
| bob@wallet.local | bob-pass-123 | CUSTOMER |

## Configuration

| Env var | Purpose | Dev default |
|---|---|---|
| `JWT_SECRET` | base64 HMAC key for access tokens (≥ 32 bytes); **required in prod** | built-in dev value |
| `DB_*`, `POSTGRES_*` | database connection (see `.env.example`) | compose defaults |

Generate a production secret with `openssl rand -base64 32`.

## Database roles

| Role | Used by | Privileges |
|---|---|---|
| `postgres` | container internals, test cleanup | superuser |
| `wallet_owner` | Flyway | owns the schema (DDL) |
| `wallet_app` | the running service | DML on ordinary tables; **SELECT/INSERT only** on `transactions` and `ledger_entries` |

See [ADR-001](docs/decisions.md).

## Troubleshooting

**`PKIX path building failed` when Maven downloads dependencies.** Antivirus HTTPS scanning (e.g. Avast Web Shield) or a corporate proxy is re-signing TLS traffic with a root certificate that Windows trusts but the JDK doesn't. Either exclude Maven Central from HTTPS scanning, or tell the JDK to use the Windows trust store:

```powershell
setx MAVEN_OPTS "-Djavax.net.ssl.trustStoreType=Windows-ROOT"   # then open a new terminal
```
