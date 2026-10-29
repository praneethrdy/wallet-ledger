# Troubleshooting

Problems hit while building and deploying Wallet Ledger, and ones likely to come up later, with
the fix for each. Add new entries at the end of the matching section as they happen.

**First step for any problem:** read the log.
- **Render:** open the service → **Logs** (or the failed deploy). Scroll to the bottom; look for
  `ERROR`, `Exception`, `Caused by`, `Exited`, `Timed out`.
- **Local backend:** the terminal running `./mvnw spring-boot:run`.
- **Local frontend:** the terminal running `npm run dev` / `npm start`, and the browser console.

Before sharing a log, replace any password or secret in it with `***`.

---

## Deployment (Render, Neon)

### Backend deploy fails: `UnknownHostException: ...neon.techT` (happened)
**Cause:** typo in `DB_URL` (an extra `T` after `neon.tech`).
**Fix:** Render → backend → Environment → `DB_URL` must be exactly
`jdbc:postgresql://<neon-host>/neondb?sslmode=require`, with no extra characters, spaces or quotes.
Save, rebuild, and deploy.

### `The connection attempt failed` / `Connection refused`
**Cause:** wrong host, or Neon's pooled host.
**Fix:** in Neon → Connect, turn **Connection pooling OFF** and copy the host again (no `-pooler`).
Keep `?sslmode=require`.

### `password authentication failed for user "wallet_app"` (or `wallet_owner`)
**Cause:** the password in Render doesn't match the one set in Neon.
**Fix:** set a new one in Neon's SQL Editor and paste the same value into Render:
```sql
ALTER ROLE wallet_app PASSWORD 'new-password-here';   -- or wallet_owner
```

### `permission denied for schema public` / Flyway can't create tables
**Cause:** `docs/neon-setup.sql` wasn't run, or ran only partly.
**Fix:** run the whole script in Neon's SQL Editor as the default owner role. If a role already
exists, skip its `CREATE ROLE` line and run the rest.

### `Illegal base64 character` at startup
**Cause:** `JWT_SECRET` was generated with `base64url` (contains `-` or `_`).
**Fix:** generate it with `toString('base64')` (see deploy-guide step 0), update Render, redeploy.
Everyone has to sign in again.

### `Could not resolve placeholder` / `... is required` / `SESSION_SECRET must be set`
**Cause:** a required environment variable is missing or misspelled.
**Fix:** compare the service's Environment tab with the lists in [deploy-guide.md](deploy-guide.md).
Production deliberately has no defaults for secrets.

### `No open ports detected` / deploy times out
**Cause:** the backend isn't listening on the port Render expects.
**Fix:** backend needs `PORT=8080`. Frontend start command must be `npm run start -- -p $PORT`.
A "No open ports detected, continuing to scan" line *during* a slow startup is normal.

### `OutOfMemoryError` / Render says "Out of memory"
**Cause:** the free plan's 512 MB is tight for Spring Boot.
**Fix:** keep `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`. If it still happens, add
`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC`, or move
the backend to a paid instance.

### Site takes ~1 minute to load the first time, or sign-in says "The server is waking up" (happened)
**Cause:** free Render services sleep after ~15 minutes idle (cold start). The frontend wakes first;
the backend (Spring Boot on a small CPU) takes 1 to 2 minutes. Until then, backend calls fail: the
frontend gives up after 30 s, or Render's edge answers 502/503/504, and the app shows
"The server is waking up or briefly unreachable... Try again in about a minute." Not a bug.
**Fix:** wait, or use a paid instance. An uptime pinger can keep it awake, but the free plan's
monthly hours are limited.

### Login says "Something went wrong on our side" on the live site (happened)
**Cause:** several logins at once overloaded the backend's tiny CPU (Argon2 hashing is
deliberately expensive) and requests timed out. One login on an idle server takes ~2 s.
**Fix:** wait a minute and try again; check `/actuator/health` responds quickly. If it happens
with normal traffic, use a paid instance (or, as a trade-off, lower the Argon2 cost in
`SecurityConfig.passwordEncoder`; existing hashes keep working).

### Login page doesn't show "Try it without signing up"
**Cause:** `DEMO_LOGIN_EMAIL` / `DEMO_LOGIN_PASSWORD` not set on the **frontend**.
**Fix:** add them (same values as the backend's `BOOTSTRAP_DEMO_*`), then **redeploy**: the login
page is pre-rendered at build time.

### Demo login fails with "Invalid email or password"
**Cause:** frontend `DEMO_LOGIN_*` don't match backend `BOOTSTRAP_DEMO_*`, or the demo account was
created earlier with a different password (the bootstrap never changes existing users).
**Fix:** make both match what the account was created with. To start the demo account over, ask
for help resetting it in the database.

### No admin account / can't reach the staff console
**Cause:** `BOOTSTRAP_ADMIN_*` were missing on the backend's first start, or the password was
shorter than 10 characters (the log says so).
**Fix:** set both and redeploy; the account is created on the next start if it doesn't exist.

### "Add money" shows no Pay button / deposit stays "Waiting for the payment provider"
**Cause:** demo payments are off.
**Fix:** set `DEMO_PAYMENTS_ENABLED=true` on **both** backend and frontend, redeploy both.
With it off, the backend's demo endpoint answers 404 by design.

### Users get logged out unexpectedly
**Cause (most likely):** the frontend runs on more than one instance (e.g. moved to Vercel or
scaled up on Render), so two instances refreshed the same token; the backend treats a reused
refresh token as theft and revokes the session.
**Fix:** keep the frontend on **one** instance. Running several would need the refresh lock moved
to shared storage (e.g. Redis), see `web/src/lib/session/refresh.ts`.

### Changed `SESSION_SECRET` and everyone was signed out
Expected: old cookies can't be decrypted with the new secret. Users just sign in again.

### "Ask" doesn't appear in the top bar
**Cause:** `LLM_API_KEY` or `LLM_MODEL` isn't set on the **frontend**, or you're signed in as staff
(the assistant is for customers).
**Fix:** set both on the frontend service and redeploy.

### Assistant says "The AI service is busy right now (its free quota)"
**Cause:** the provider returned HTTP 429: the free tier's per-minute or per-day quota is used up.
**Fix:** wait a minute (per-minute limits) or until tomorrow (daily limits). Lower
`ASSISTANT_DAILY_LIMIT`, switch to another free model/provider, or add billing.

### Assistant says "The AI service isn't responding"
**Cause:** wrong key, wrong model id, wrong `LLM_BASE_URL`, or the model doesn't support tool calling.
The frontend log shows `Assistant: provider: Model returned HTTP <status>`
(401/403 = key, 404 = model or URL, 400 = often tool calling unsupported).
**Fix:** copy the model id exactly from the provider's console; pick a model that supports
function/tool calling; check the base URL has no trailing path like `/chat/completions`.

### Assistant answers but the numbers look wrong
Totals come from `summarize_transactions`, computed in code. Check the period it used: the answer
should mention it, and dates are India time. If the model ignored the tool and invented a number,
try a stronger model; the system prompt forbids doing arithmetic itself.

### "You've asked N questions today, the daily limit"
Working as intended (`ASSISTANT_DAILY_LIMIT`, default 20). It resets at midnight IST or on restart.

---

## Local development

### Backend won't start: `Detected applied migration not resolved locally` (happened)
**Cause:** the local database ran a migration whose file was later deleted (the removed Razorpay
`V4`).
**Fix (local dev DB only):** remove what it added and its history row, e.g.
```
docker exec wallet-ledger-postgres-1 psql -U postgres -d wallet -c "ALTER TABLE deposits DROP COLUMN IF EXISTS provider; DELETE FROM flyway_schema_history WHERE version = '4';"
```
Or reset local data completely: `docker compose down -v && docker compose up -d` (deletes all
local data). **Never do either on Neon.**

### Postgres container missing / backend health is DOWN (happened)
**Cause:** the `wallet-ledger-postgres-1` container was removed (data volume still intact).
**Fix:** `docker compose up -d postgres`. It reattaches the `pgdata` volume, so no data is lost.
If Docker itself hangs, restart Docker Desktop.

### Port 5432 already in use
**Cause:** a native Windows PostgreSQL service owns 5432.
**Fix:** this project uses **5433** for Docker Postgres (see `docker-compose.yml`, `POSTGRES_PORT`).

### `PKIX path building failed` (Maven) or `SELF_SIGNED_CERT_IN_CHAIN` (npm/Node)
**Cause:** antivirus HTTPS scanning (Avast) re-signs TLS traffic.
**Fix:** trust the Windows certificate store, never disable TLS verification:
- Maven: `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`
- Node: `NODE_OPTIONS=--use-system-ca`
- Git: `git config http.sslBackend schannel` (repo-local)

### `npm ci` crashes with `edgesOut` / peer dependency errors
**Cause:** an npm 10 bug with optional peer chains.
**Fix:** keep `legacy-peer-deps=true` in `web/.npmrc` and Vitest on 4.x.

### `node` is the wrong version / "not recognized"
**Fix:** use Node 22 (`web/.nvmrc`). Reopen the terminal after installing. Any version works for
generating secrets.

### `./mvnw: Permission denied` on CI (exit 126)
**Fix:** `git update-index --chmod=+x backend/mvnw`, commit, push.

### Next.js: "unstable value `Date.now()` while prerendering" in server logs (happened)
**Cause:** something reads the clock during Next's prerender pass (it was the session cookie's
expiry check).
**Fix:** call `await connection()` (from `next/server`) before that work; see
`web/src/lib/session/session.ts`.

### Next.js: data shows stale after navigating back
**Cause:** client caches kept older pages than the fresh server render.
**Fix:** cursor lists use `gcTime: 0` (`components/data/cursor-list.tsx`); keep that for new lists.

---

## Tests

### Playwright: "Backend not reachable"
**Fix:** start Postgres and the backend first: `docker compose up -d`, then
`cd backend && ./mvnw spring-boot:run`. Build the web app (`npm run build`) before `npm run test:e2e`.

### Playwright: `strict mode violation ... resolved to 2 elements` (happened)
**Cause:** Next 16 keeps the previous page mounted but hidden, so text appears twice in the DOM.
**Fix:** match visible elements only: `page.getByText("...").filter({ visible: true })`.

### Playwright: random failures after ~1 minute, `UNAUTHORIZED` from the API helper (happened)
**Cause:** the backend ran with a 75 s token TTL (for the refresh test) and the helper cached tokens.
**Fix:** the helper renews cached tokens every 30 s (`web/e2e/helpers.ts`).

### Silent-refresh test is skipped
Expected unless the backend runs with `SECURITY_AUTH_ACCESS_TOKEN_TTL=75s` and the suite with
`E2E_SHORT_TOKEN_TTL=1`.

### Backend: a guard test fails after adding an endpoint
- `EndpointAccessRulesIT`: add an access annotation (`@CanMoveMoney`, `@AdminOnly`, ...).
- `IdempotencyKeyRequiredIT`: add the new mutating endpoint to its lists.
- `OpenApiExportIT` / CI "contract is committed": run `./mvnw verify` and commit `backend/openapi.json`,
  then `cd web && npm run gen:api` and commit `web/src/lib/api/schema.d.ts`.

### Accessibility test fails with `color-contrast`
**Fix:** use the `*-text` colour tokens for text on tinted backgrounds (`text-brand-text`,
`text-credit-text`, ...) and keep filled buttons on `--primary` (see `web/src/app/globals.css`).

---

## Git and GitHub

### Commits must not credit Claude
Never add `Co-Authored-By: Claude` lines. If one slips in, rewrite the message before pushing.

### Accidentally committed a secret
1. **Rotate it immediately** (new value in Neon/Render); deleting the commit is not enough.
2. Then remove it from history or ask for help doing so, and force-push only if you understand
   the consequences.

### Line-ending warnings (`LF will be replaced by CRLF`)
Harmless on Windows. If a generated file shows as modified with no real diff, `git restore <file>`.
