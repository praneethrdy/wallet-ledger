# Learning log

Concepts learned at each step, in my own words where possible.

---

## Phase 1 · Step 1: Skeleton, schema, triggers, Testcontainers

**Schema migrations (Flyway).** The database schema is versioned code. `V1__…sql`, `V2__…sql` run once, in order, and are recorded in `flyway_schema_history` with a checksum. Once a migration has shipped, it is immutable: you fix forward with a new `V3`. `ddl-auto=validate` means Hibernate only *checks* that entities match the schema and never changes it.

**Double-entry bookkeeping.** Every money movement is one *transaction* (the header) with two or more *entries* (the legs). Money leaves one account (DEBIT) and arrives in another (CREDIT). Per currency, debits = credits, so money is never created or destroyed. It only moves, and external money enters through a system account (`EXTERNAL_FUNDING`) that is allowed to go negative.

**Append-only ledger.** Ledger rows are never updated or deleted. A mistake is fixed by posting a *reversing* transaction, so the history of what happened, including the mistake, is preserved for audit.

**Deferred constraint triggers.** A normal constraint is checked per statement. A transfer, though, inserts a DEBIT and then a CREDIT, and in between the transaction is unbalanced. `DEFERRABLE INITIALLY DEFERRED` moves the check to COMMIT, when the whole transaction is visible. If it fails, the whole commit rolls back.

**Defence in depth.** The rules are enforced in Java (`LedgerService`, Step 5), by triggers, *and* by privileges (the app role cannot UPDATE/DELETE ledger tables). Each layer catches what the others might miss.

**Least-privilege DB roles.** Migrations run as the schema owner and the app runs as a DML-only role. A compromised app cannot `DROP TABLE` or rewrite history.

**Testcontainers.** Integration tests run against a real Postgres 16 in Docker, not H2. H2 has no plpgsql triggers, no `FOR UPDATE` semantics identical to Postgres, and no `citext`/`jsonb`, so it would let bugs pass. One container is shared by all tests, and data is truncated before each test.

**Unit vs integration tests in Maven.** Surefire runs `*Test` in the `test` phase. Failsafe runs `*IT` in the `integration-test`/`verify` phase. `./mvnw verify` runs both.

**Gotchas hit in this step.**
- plpgsql resolves `NEW.some_column` even inside a `CASE` branch that never executes. A trigger shared by two tables must use `IF ... ELSE` to read table-specific fields.
- Row-level UPDATE/DELETE triggers don't fire on `TRUNCATE`. It needs its own statement-level trigger, or a REVOKE.
- A foreign key from an append-only table to a table with a TTL cleanup can't work. Every `ON DELETE` option either blocks the cleanup, updates the append-only row, or deletes it (ADR-004).

_Interview Q&A for this step: to be filled in after review._
