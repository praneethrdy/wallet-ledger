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

_Interview Q&A: skipped for now; answer later. The break-it exercise on the deferred trigger is also pending._

---

## Phase 1 · Step 2: Errors, correlation ID, Money, Clock

**RFC 7807 ProblemDetail.** A standard JSON shape for errors (`type`, `title`, `status`, `detail`, `instance`) that can be extended with custom fields. We add a stable `code`, because clients should switch on `code`, never parse `detail`. One `@RestControllerAdvice` maps every exception, so no controller formats errors itself.

**400 vs 409 vs 422.** 400 means the request is broken. 422 means it's understood but refused by a business rule. 409 means it clashes with the current state of the resource.

**Never leak internals.** Only exceptions we chose to make public (`ApiException`) show their message. Everything else is logged server-side and the client gets a generic 500 with a correlation ID. Even framework messages can leak, e.g. Jackson errors that name Java classes.

**Correlation ID + MDC.** MDC is a per-thread map that the logger prints on every line. Put the request ID there in a filter and every log line for that request is greppable by one ID. Clear it in `finally`, because threads are reused across requests. Validate client-supplied IDs to avoid log injection (CR/LF).

**Money as a value object.** `long` minor units plus a currency. `Math.addExact` throws on overflow instead of wrapping (`Long.MAX_VALUE + 1` becomes negative!). Arithmetic across currencies is refused. Whether a value may be negative is a business rule, not a property of Money.

**Injected Clock.** `Instant.now()` makes time-based logic untestable. Inject a `Clock` and tests can freeze or advance time.

**UUID v7.** Time-ordered UUIDs append to the right edge of the B-tree index (fast inserts, natural ordering), while 74 random bits keep them unguessable.

**Test slices.** `@WebMvcTest` starts only the web layer (controllers, advice, filters, Jackson), so there's no DB or Docker and it's fast. Use it for HTTP-contract tests; use `@SpringBootTest` + Testcontainers when the DB matters.

_Interview Q&A for this step: pending._

---

## Phase 1 · Step 3: Authentication

**Hashing passwords: why Argon2id.** A password hash must be *slow* and *memory-hard* so that a stolen database can't be brute-forced at billions of guesses per second on GPUs. SHA-256 is fast (bad here). bcrypt is slow but not memory-hard. Argon2id is both. Each hash embeds its salt and parameters (`$argon2id$v=19$m=19456,t=2,p=1$salt$hash`), so the cost can be raised later.

**Why SHA-256 is fine for refresh tokens.** They are 256 random bits, so there is nothing to guess. A fast, deterministic hash lets us look the token up, and a DB leak doesn't leak usable tokens.

**Access vs refresh tokens.** A short-lived JWT (15 min) is verified with just a signature check, with no DB call. A long-lived opaque refresh token (7 days) lives in the DB, so it *can* be revoked. Short access tokens bound how long a stolen or revoked one stays useful.

**JWT anatomy.** `header.payload.signature`, each base64url. The payload is readable by anyone, so it holds no secrets; it is protected only against *changes*. Changing `role: CUSTOMER` to `ADMIN` breaks the signature, and we test exactly that. Pin the algorithm (HS256 only) so `alg: none` tricks fail.

**Refresh-token rotation and reuse detection.** Each refresh returns a new token and revokes the old one, all in one *family*. If an old token shows up again, two parties have copies. We can't tell which is the thief, so we kill the whole family and audit it.

**Transactions that must commit on failure.** `@Transactional(noRollbackFor = AuthenticationFailedException.class)` lets us return 401 while still committing the family revocation and the audit row.

**No user enumeration.** Use the same error for an unknown email and a wrong password. Spend the same time on both (verify against a dummy hash), and record the real reason only server-side.

**Stateless security.** No server session: each request carries its proof. CSRF protection is for cookie-based auth; with bearer headers there is nothing for a malicious site to forge.

**Gotchas hit in this step.**
- A test expected one reuse audit event, but presenting the *second* (family-revoked) token is also a reuse, so 2 was correct. The fix was to assert the count after each step, which makes the test stricter rather than weaker.
- Security failures happen in filters, before controllers, so `@RestControllerAdvice` never sees them. A custom `AuthenticationEntryPoint`/`AccessDeniedHandler` keeps the ProblemDetail shape.
- `@WebMvcTest` pulls in Spring Security's auto-config. Exclude it for tests that aren't about security.

_Interview Q&A for this step: pending._
