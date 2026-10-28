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

---

## Phase 1 · Step 4: RBAC and ownership

**Authentication vs authorization.** Authentication asks *who are you?* (Step 3: the JWT). Authorization asks *what may you do?*, and it has two layers:
- **Role (RBAC):** may a SUPPORT user call `/reverse` at all? Checked at the endpoint, and failure is **403**.
- **Ownership:** is wallet X *yours*? Checked in the service, and failure is **404** (ADR-009).

**Why 404 for someone else's resource.** A 403 confirms that the wallet exists. Scoping the query (`findByIdAndOwnerUserId`) makes "missing" and "not yours" the same path, with the same response and nothing to probe.

**Never trust client-supplied identity.** The user ID always comes from the verified token (`AuthUser`), never from `?userId=` or the body. Otherwise changing one number in a request gives access to another account, which is called IDOR (Insecure Direct Object Reference) and is consistently near the top of the OWASP API Security Top 10.

**Method security.** `@PreAuthorize("hasRole('ADMIN')")` runs an access check before the method executes. Wrapping it in a named meta-annotation (`@AdminOnly`) writes each rule once and makes controllers read like the RBAC table.

**Exceptions take different paths.** Filter-level failures (no or bad token) go to the `AuthenticationEntryPoint`. `@PreAuthorize` failures happen inside the controller call and reach `@RestControllerAdvice`. Both must produce the same ProblemDetail, and a catch-all handler can silently turn 403s into 500s.

**Guard tests.** Some rules ("every endpoint declares who may call it") are best enforced by a test that inspects the application itself. We proved it by adding an unprotected endpoint and watching the build fail with the endpoint's name.

_Interview Q&A for this step: pending._

---

## Steps 5–14 (built in one go at the user's request)

**Derived balances (Step 5).** A balance is a query (`SUM(credits) − SUM(debits)`), not a column. There is nothing to keep in sync, so nothing can drift. The `(account_id, created_at, id)` index keeps it fast.

**Keyset pagination.** "Rows older than (created_at, id) of the last row I saw" instead of `OFFSET n`. It's fast at any depth and stable while new rows arrive. Running balances come from one SUM at the top of the page, then walking down the page subtracting each entry's effect.

**Pessimistic locking in a global order (Step 6).** `SELECT … FOR UPDATE` on every wallet a transaction touches, always in UUID order, so two transfers can never each hold the lock the other wants. Check the balance *after* the lock, at READ COMMITTED: each statement sees everything committed before it ran.

**Why READ COMMITTED, not REPEATABLE READ, for transfers.** REPEATABLE READ takes its snapshot at the first statement, which can be *before* we waited for the lock. We'd then compute the balance without the debit that just committed. READ COMMITTED re-snapshots per statement.

**Idempotency (Step 7).** Reserve the key before touching money. Store the response in the *same* transaction as the money movement, so a crash can't leave "money moved, key not marked". Use the reservation timestamp as a fencing token so a slow original and a takeover can't both commit.

**Webhooks (Step 8).** Verify the HMAC over the exact raw bytes, compare in constant time, put the timestamp inside the MAC, and allow ±5 min. Make processing idempotent at two levels: the event id, and the deposit state behind a row lock. Store forgeries under synthetic ids so they can't squat on a real event's id.

**Reversals (Step 9).** Never edit; post the mirror image. A partial unique index makes "reversed at most once" a database fact, not a hope.

**Read models (Steps 10–11).** Aggregate in SQL, not in Java loops. Netting per transaction across all your wallets means moving money between your own wallets is neither inflow nor outflow. Use REPEATABLE READ for multi-query read pages so the numbers agree with each other.

**Reconciliation (Step 12).** Re-derive every invariant from raw rows on one consistent snapshot. Test it by injecting corruption the way it would really happen: a superuser with triggers disabled.

**Observability (Step 13).** A correlation ID in every log line and error body; JSON logs in prod; business metrics (transfers by outcome, idempotency replays, webhook outcomes, lock wait, reconciliation mismatches) next to the HTTP ones.

**Shipping (Step 14).**
- A multi-stage Docker build ending in a non-root JRE image.
- CI runs the real-database test suite and a coverage gate, and fails if the API contract (`openapi.json`) changed without being committed.

**Gotchas hit in these steps.**
- **JPA inserts are invisible to JDBC until flush.** Inserting an account with `save()` and then posting to it via `JdbcTemplate` in the same transaction fails with "unknown account". Use `saveAndFlush`.
- **`citext` columns come back as `PGobject` from `queryForList`.** Select `email::text`.
- **Every cached Spring test context keeps its own connection pool.** With several contexts sharing one container, Postgres ran out of connections. Use small test pools and a higher `max_connections`.
- **A "successful" coverage gate can be an absent one.** The JaCoCo plugin edit had silently not applied. Always look at the report, not just "BUILD SUCCESS".
- **Test expectations can be wrong too.** For example, reversing a deposit after part of it was spent *should* fail. Fix the test's premise, never weaken the assertion.

_Interview Q&A for these steps: pending._
