# Frontend brief: Wallet & Ledger web app (`web/`)

> **Status:** draft spec for review. Edit anything you disagree with *before* the build starts.
> **For the building session:** read this whole file, then `README.md`, then `backend/openapi.json`. Build in the order of section 10 and verify each step before moving on.

---

## 1. Goal

A web app over the existing backend that makes its guarantees **visible**: balances that always add up, transfers that can't double-submit, deposits confirmed by a signed provider webhook, and an audited staff console. It's a portfolio piece, so correctness, security and polish count more than feature count.

**Scope:** customer app + staff console (ADMIN and SUPPORT). Both are in one Next.js app, with navigation driven by the user's role.

## 2. Ground rules

- All frontend code lives in `web/`. **Don't change `backend/`** unless the brief says so or the user asks.
- If the backend truly must change (a bug, or a missing endpoint the user approves): change it, re-run `cd backend && ./mvnw verify`, and commit the regenerated `backend/openapi.json` in the same commit.
- **`backend/openapi.json` is the contract.** API types are **generated** from it, never hand-written (section 4).
- Never put a `Co-Authored-By: Claude` (or any Claude attribution) line in commits.
- Machine notes for this Windows box:
  - **Node 18 is installed, but Next.js 16 needs Node ≥ 20.9.** Ask the user to install Node 22 or 24 LTS first; don't work around it.
  - Avast intercepts HTTPS. If `npm install` fails with a certificate error, use `npm config set cafile` (pointing at an exported Avast root certificate) or ask the user. **Never disable TLS verification.**
  - The dev database is on port **5433**, because a native Postgres service owns 5432.

## 3. Stack

| Concern | Choice |
|---|---|
| Framework | **Next.js 16** (App Router, React 19, Server Components + Server Actions), TypeScript `strict` |
| Styling and UI | **Tailwind CSS 4** + **shadcn/ui** (Radix primitives) + lucide icons |
| Forms | React Hook Form + Zod (client UX), the same Zod schemas re-validated in Server Actions |
| Server data | Server Components fetch through the BFF client (section 5); **TanStack Query** only where the client must poll or paginate (deposit status, infinite entry lists) |
| Charts | Recharts (30-day cash flow, balance sparkline) |
| API types | `openapi-typescript` (7.x), generated from `../backend/openapi.json` into `web/src/lib/api/schema.d.ts`; `openapi-fetch` as the typed client |
| Session | encrypted, signed HttpOnly cookie (`iron-session`, or `jose` JWE) |
| Tests | Vitest + Testing Library (units: money, error mapping, forms); **Playwright** end-to-end against the real backend |
| Lint and format | ESLint (Next config) + Prettier; `tsc --noEmit` in CI |
| Package manager | npm (lockfile committed) |

Pin exact versions in `package.json`.

## 4. The contract and codegen

- `npm run gen:api` runs `openapi-typescript ../backend/openapi.json -o src/lib/api/schema.d.ts`. Commit the output.
- Wrap `openapi-fetch` in `src/lib/api/client.ts`, which is **server-only** (`import "server-only"`).
- CI regenerates the types and fails on a diff, the same pattern as the backend's `openapi.json` check.

### Backend facts the UI must respect

- **Money:** integers in **minor units**. `50000` = ₹500.00.
  - Display with `Intl.NumberFormat("en-IN", { style: "currency", currency })`.
  - Parse user input from the **string** (`"500.50"` → `50050`), never via `parseFloat` × 100.
  - Reject more than 2 decimals for INR.
  - Put this in `src/lib/money.ts` with thorough unit tests.
- **Errors:** RFC 7807 `application/problem+json` with a stable `code`, plus `errors[]` for field validation.
  - Branch on `code` only, never on `detail`.
  - Map codes to friendly copy in **one** module, e.g. `INSUFFICIENT_FUNDS` → "Not enough balance in this wallet".
  - Show `correlationId` on unexpected errors ("Reference: …") for support.
- **Pagination:** keyset. Pass `nextCursor` back as `?cursor=`; `null` means the end. Never compute offsets.
- **404 means "not found or not yours"** (ADR-009). Render it as a not-found page, never as "forbidden".
- **Roles:** `CUSTOMER`, `SUPPORT`, `ADMIN`.
  - ADMIN can hold wallets but **can't transfer or deposit** (403).
  - SUPPORT has no wallets.
  - Hide what a role can't do, but still handle a 403 gracefully.

## 5. Architecture: BFF (backend-for-frontend)

```
Browser ──(HttpOnly session cookie)──▶ Next.js server ──(Bearer JWT + Idempotency-Key)──▶ Spring backend
```

- **The browser never sees a JWT.**
  - Login is a Server Action. It calls `POST /api/v1/auth/login` and stores `{accessToken, refreshToken, expiresAt, user}` in the encrypted session cookie: `HttpOnly`, `Secure` in prod, `SameSite=Lax`, `Path=/`.
  - Nothing goes in `localStorage`, and no tokens in client components.
- **All backend calls go through `src/lib/api/client.ts` on the server.** It reads the session and adds `Authorization`. The backend URL comes from the server-only env var `API_BASE_URL` (default `http://localhost:8080`).
- **Refresh: critical.**
  - The backend has **strict refresh-token reuse detection**. Presenting an already-used refresh token revokes the whole session family (ADR-008).
  - So refresh **at most once per session at a time**:
    - refresh ahead of expiry (e.g. under 60s left) inside the server client, using a per-session in-flight promise (single-flight);
    - write the rotated tokens back to the cookie;
    - on a refresh 401, clear the session and redirect to `/login?expired=1`.
  - Never refresh from the browser.
- **CSRF.** The BFF *does* use a cookie, so CSRF protection matters here (unlike the backend; see ADR-008).
  - Use Server Actions, which Next.js checks for same-origin automatically.
  - Any custom `route.ts` that mutates must verify the `Origin` header.
  - Keep `SameSite=Lax`.
- **Idempotency: important.**
  - Every mutating backend call (except `/auth/*`) needs an `Idempotency-Key`.
  - Generate the key **when the form renders** (a hidden field with `crypto.randomUUID()`), **not** per network attempt. A double-click, a retry or a slow-network resubmit then reuses the same key, and the backend replays instead of moving money twice.
  - After a successful submit, a fresh form gets a fresh key.
  - Surface `Idempotent-Replayed: true` quietly ("Already submitted, showing the original result").
- **Logout:** call `POST /auth/logout` with the refresh token, then destroy the cookie. "Log out everywhere" calls `/auth/logout-all`.
- **Route protection:** `middleware.ts` redirects unauthenticated users to `/login`. Role checks happen in server layouts (`/admin/**` needs ADMIN, `/support/**` needs SUPPORT or ADMIN); the backend remains the real authority.
- **Security headers:** a CSP without `unsafe-inline` scripts where possible, `X-Frame-Options: DENY` (or `frame-ancestors 'none'`), `Referrer-Policy`, `X-Content-Type-Options`. No secrets in `NEXT_PUBLIC_*`.

## 6. Screens

### Public
- **/login**: email + password; generic error ("Invalid email or password"); `?expired=1` banner.
- **/register**: email + password (min 10 characters, show the rule); on success, sign in.

### Customer (CUSTOMER; ADMIN sees its own wallets but no money movement)
- **/** Dashboard (`GET /dashboard/summary`):
  - total balance per currency;
  - this month's inflow and outflow;
  - a 30-day cash-flow bar chart (inflow vs outflow per day);
  - the last 8 transactions.
  - Note for the UI copy: transfers between your own wallets count as neither in nor out.
- **/wallets**: list with balances; "New wallet" dialog (name; currency INR, USD or EUR; default INR); "Copy wallet ID" (people need it to receive transfers).
- **/wallets/[id]**:
  - balance, status badge (ACTIVE or FROZEN), and a 30-day balance sparkline (`balance-history?days=30`);
  - an entries table with **infinite scroll** via cursor, showing the running balance per row, CREDIT in green and DEBIT in red;
  - FROZEN → banner: "This wallet is frozen; transfers in and out are blocked".
- **/transfer**:
  - from (own ACTIVE wallets with balances), to (wallet ID paste field), amount, optional note (140 chars);
  - **review step** before submit;
  - the result screen shows the reference (`TXN-…`) and both legs. The recipient's wallet name appears as "Another user's wallet", which is the backend's privacy masking.
  - Errors by code: `INSUFFICIENT_FUNDS`, `WALLET_FROZEN`, `CURRENCY_MISMATCH`, `SAME_WALLET_TRANSFER`, `NOT_FOUND` (destination).
- **/deposit**:
  - pick a wallet and amount → `POST /deposits` returns PENDING plus a `providerReference`;
  - then a **mock payment page**, dev only, flagged by the server-only env var `MOCK_PROVIDER_ENABLED=true`, with "Pay" / "Fail payment" buttons. A Server Action calls `POST /dev/mock-provider/deposits/{ref}/complete?outcome=…`, and the backend signs and delivers the webhook to itself;
  - **poll** `GET /deposits/{id}` (TanStack Query, every 1.5s, stop on SUCCEEDED or FAILED, max ~60s) and show the status transition.
  - The UI must not assume success: the balance changes only when the webhook lands.
- **/transactions**:
  - filters (wallet, type, date range, reference) mapped to the query params; cursor pagination.
  - The list shows `netAmount` (+ in, − out, 0 between your own wallets).
  - **/transactions/[id]** shows all legs, reversal links both ways ("Reversed by …" / "Reversal of …").
- **/settings**: profile (`GET /me`), "Log out everywhere".

### Staff console (role-gated layouts)
- **/admin** (ADMIN): overview cards (`GET /admin/overview`): today's volume per currency, transfers today, failed webhooks (24h), last reconciliation status.
- **/admin/wallets** (ADMIN):
  - search by email or status; cursor pagination;
  - **Freeze / Unfreeze** with a confirm dialog;
  - show owner email and real wallet names (staff view).
- **/admin/transactions/[id]/reverse** (ADMIN):
  - show the original transaction with its legs; a required reason (max 500 chars);
  - a confirm dialog that spells out "This posts a new REVERSAL transaction; history is not changed";
  - handle `ALREADY_REVERSED`, `REVERSAL_NOT_ALLOWED` and `INSUFFICIENT_FUNDS_FOR_REVERSAL` clearly.
- **/admin/reconciliation**:
  - the run list (SUPPORT and ADMIN), with run detail showing findings grouped as ERROR or WARNING;
  - "Run now" for ADMIN only (`POST /admin/reconciliation/run`; it's synchronous, so show progress).
- **/support/users** (SUPPORT, ADMIN): email search → user detail → their wallets with balances.
- **/support/transactions**: search by reference, wallet, type and date. Link a row to the reverse page for ADMIN.
- **/support/webhook-events**: filter by status; expandable JSON payload; REJECTED and FAILED highlighted.
- Show a small notice in the staff console: "Your lookups are recorded in the audit log", which is true, since the backend audits every support read.

### Every screen
Loading skeletons, empty states, error states (with `correlationId`), keyboard accessibility, visible focus, labelled inputs, a responsive layout (mobile to desktop), and light/dark via a theme toggle.

## 7. Suggested layout of `web/`

```
web/
  src/app/(public)/login, register
  src/app/(app)/layout.tsx           # authenticated shell, role-aware nav
  src/app/(app)/page.tsx             # dashboard
  src/app/(app)/wallets, transfer, deposit, transactions, settings
  src/app/(app)/admin/..., support/...
  src/lib/api/client.ts schema.d.ts  # server-only typed client + generated types
  src/lib/session.ts                 # cookie session + single-flight refresh
  src/lib/money.ts errors.ts         # minor-unit formatting/parsing, ProblemDetail → UI copy
  src/components/ui/...              # shadcn
  tests/e2e/...                      # Playwright
```

## 8. Testing

- **Unit (Vitest):**
  - `money.ts` (parsing edge cases: `"0.1"`, `"1,000.50"`, `"1.005"` rejected, negatives rejected, very large numbers);
  - error-code mapping;
  - Zod schemas;
  - the idempotency-key-per-form behaviour.
- **End-to-end (Playwright),** against the real backend (`docker compose up -d` + `./mvnw spring-boot:run`, with dev seed data):
  1. Log in as alice; the dashboard shows totals and the chart.
  2. Create a wallet; transfer to bob's wallet; the balance and entries update; the transaction detail shows both legs.
  3. Double-click "Confirm transfer" → exactly **one** transaction (the idempotency key is reused).
  4. Deposit → mock "Pay" → the status polls to SUCCEEDED → the balance increases by exactly the amount.
  5. A transfer above the balance shows the insufficient-funds message.
  6. Bob opening alice's wallet URL → not-found page.
  7. Admin: freeze alice's wallet → alice's transfer is blocked → unfreeze.
  8. Admin: reverse a transfer with a reason → the original shows "Reversed by"; reversing again shows the already-reversed message.
  9. Support: find alice, view her wallets, see webhook events; the admin-only "Run now" isn't shown.
  10. Session expiry: after the access token expires, navigation still works (silent refresh); after logout, protected pages redirect to `/login`.
- **Accessibility:** run `@axe-core/playwright` on the main pages, with no serious violations.

## 9. Definition of done

- [ ] `cd web && npm ci && npm run build` succeeds; `npm run lint` and `tsc --noEmit` are clean.
- [ ] `npm test` (Vitest) and `npm run test:e2e` (Playwright, against the real backend) pass.
- [ ] No JWT or refresh token is ever readable by browser JavaScript (check DevTools → Application: only an HttpOnly cookie).
- [ ] Double-submitting any money form creates exactly one backend transaction.
- [ ] Every error state shows friendly copy keyed by `code`; unexpected ones show the `correlationId`.
- [ ] The mock provider is reachable only when `MOCK_PROVIDER_ENABLED=true`.
- [ ] CI: a `.github/workflows/web-ci.yml` job (lint, typecheck, unit tests, build, and the generated-types drift check) on `web/**` changes. Playwright e2e can run in CI by starting Postgres and the backend jar, or stay local-only if that's too slow: document the choice.
- [ ] `web/README.md` covers setup, env vars (`API_BASE_URL`, `SESSION_SECRET` ≥ 32 chars, `MOCK_PROVIDER_ENABLED`), scripts, and architecture (BFF, refresh single-flight, idempotency). The root README links to it.
- [ ] Screenshots (dashboard, transfer, deposit flow, admin console) in `docs/screenshots/` for the portfolio.

## 10. Build order

Verify each step (build, tests, and a look at the running UI) before the next.

1. Scaffold `web/` (Next 16, TypeScript strict, Tailwind 4, shadcn, ESLint/Prettier), and the codegen script plus generated types.
2. `money.ts` and `errors.ts`, with unit tests.
3. Session, BFF client, single-flight refresh; login, logout and register; middleware; the authenticated shell with role-aware navigation.
4. Dashboard; wallets list and create; wallet detail with infinite entries and sparkline.
5. Transfer flow (review step, idempotency key per form, error mapping).
6. Deposit flow with the mock provider and status polling.
7. Transactions list, filters and detail.
8. Staff console: admin overview, wallet search and freeze, reversal, reconciliation, support pages.
9. Polish: empty, loading and error states, dark mode, responsive pass, accessibility.
10. Playwright e2e suite, web CI workflow, `web/README.md`, screenshots.

## 11. Kickoff prompt for the new session

> Read `docs/frontend-brief.md` and follow it exactly. It is the spec for the `web/` frontend of this repo. Also read `README.md` and `backend/openapi.json`. Build in the order of section 10, verifying each step before the next. Don't modify `backend/` unless the brief allows it. Never add Claude attribution to commits. Start with step 1, and first confirm the Node.js version is ≥ 20.9.
