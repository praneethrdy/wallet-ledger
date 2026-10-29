# Deploy guide: how Wallet Ledger went live

The exact steps used to put this project online for free, in the order they were done.
Problems and fixes are in [troubleshooting.md](troubleshooting.md).

**Live site:** https://wallet-ledger-tb4t.onrender.com
**Backend API:** https://wallet-ledger-api-t6xu.onrender.com (health: `/actuator/health`)
**Code:** https://github.com/praneethrdy/wallet-ledger

> Never put real passwords or secrets in this file, the repo, screenshots or chat.
> They live only in Neon (the two DB role passwords) and in Render's environment variables.

---

## How the pieces fit

```
Browser ──HTTPS──> Frontend (Next.js, Render) ──HTTPS + JWT──> Backend (Spring Boot, Render) ──TLS──> PostgreSQL (Neon)
          only an encrypted                     server-side only;                                  all data lives here
          session cookie                        the browser never sees tokens
```

| Piece | Host | Region | Plan |
|---|---|---|---|
| Database (PostgreSQL 16) | Neon | AWS Asia Pacific (Singapore) | Free |
| Backend (`backend/`, Docker) | Render web service | Singapore | Free |
| Frontend (`web/`, Node) | Render web service | Singapore | Free |

**Why Render for the frontend and not Vercel:** the frontend refreshes login tokens with a
single-flight guard that works within one server process. Vercel runs many serverless instances,
so two of them could refresh the same token at once; the backend treats that as token theft and
signs the user out. Render's free plan runs one long-lived process, so the guard works as tested.

**Where data is stored:** only in Neon. Render services keep nothing permanent (redeploying loses
nothing); the browser keeps only the encrypted session cookie. Local dev data (Docker Postgres on
port 5433) is completely separate.

---

## 0. Before you start

1. Make sure the latest code is on GitHub (`main`) and the **Actions** tab is green
   (`backend-ci`, `web-ci`).
2. Generate secrets in the VS Code terminal (**Terminal → New Terminal**). Keep them in a private
   note outside the project folder.

   For **`JWT_SECRET`** (must be standard base64, the backend decodes it):
   ```
   node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
   ```
   For everything else (run once per secret):
   ```
   node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
   ```

   | Secret | Command | Used in |
   |---|---|---|
   | Owner DB password | base64url | Neon SQL + backend `DB_OWNER_PASSWORD` |
   | App DB password | base64url | Neon SQL + backend `DB_APP_PASSWORD` |
   | `JWT_SECRET` | **base64** | backend |
   | `WEBHOOK_SECRET` | base64url | backend |
   | `SESSION_SECRET` | base64url | frontend |
   | Admin password | base64url, or your own strong password (≥ 10 chars) | backend `BOOTSTRAP_ADMIN_PASSWORD` |

---

## 1. Database: Neon

1. Sign up at **neon.tech** (GitHub login is easiest).
2. **Create project:** name `wallet-ledger`, **Postgres 16**, region **AWS Asia Pacific (Singapore)**.
3. Open **SQL Editor** (database `neondb`). Paste the contents of [`neon-setup.sql`](neon-setup.sql),
   replace `REPLACE_WITH_OWNER_PASSWORD` and `REPLACE_WITH_APP_PASSWORD` with your two DB passwords
   (keep the single quotes), click **Run**. Only edit the copy in Neon, never the file in the repo.

   This creates two roles, like local Docker does:
   - `wallet_owner` owns the schema; Flyway uses it for migrations.
   - `wallet_app` is what the running backend uses: rows only, no DDL, no UPDATE/DELETE on the ledger.
4. Click **Connect**, turn **Connection pooling OFF**, and copy only the **host** (between `@` and
   `/neondb`, no `-pooler`). Build the backend URL:
   ```
   jdbc:postgresql://<host>/neondb?sslmode=require
   ```
   Do not use Neon's own `neondb_owner` user/password in the app.

---

## 2. Backend: Render

1. Sign up at **render.com** with GitHub and allow access to the repo.
2. **New + → Web Service** → `praneethrdy/wallet-ledger`:
   - Name: `wallet-ledger-api`
   - Region: **Singapore** · Branch: `main`
   - Root Directory: **`backend`** · Language: **Docker** · Instance: **Free**
   - Advanced → Health Check Path: **`/actuator/health/readiness`**
3. **Environment variables.** Use **Add from .env** (a paste box on Render, not a file). Fill this
   in an *untitled, unsaved* VS Code tab, paste it, then close the tab without saving:
   ```
   PORT=8080
   DB_URL=jdbc:postgresql://YOUR-NEON-HOST/neondb?sslmode=require
   DB_OWNER_USER=wallet_owner
   DB_OWNER_PASSWORD=YOUR-OWNER-DB-PASSWORD
   DB_APP_USER=wallet_app
   DB_APP_PASSWORD=YOUR-APP-DB-PASSWORD
   JWT_SECRET=YOUR-BASE64-SECRET
   WEBHOOK_SECRET=YOUR-WEBHOOK-SECRET
   DEMO_PAYMENTS_ENABLED=true
   BOOTSTRAP_ADMIN_EMAIL=YOUR-EMAIL
   BOOTSTRAP_ADMIN_PASSWORD=YOUR-ADMIN-PASSWORD
   BOOTSTRAP_DEMO_EMAIL=demo@walletledger.dev
   BOOTSTRAP_DEMO_PASSWORD=try-the-demo-2026
   SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
   MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info
   ```
   No spaces around `=`, no quotes. You should see 15 variables.

   | Variable | Why |
   |---|---|
   | `PORT=8080` | tells Render which port the app listens on |
   | `DB_*` | database connection, as the two roles from step 1 |
   | `JWT_SECRET`, `WEBHOOK_SECRET` | signing keys; production refuses to start without them |
   | `DEMO_PAYMENTS_ENABLED=true` | "Add money" completes with a labelled, simulated payment |
   | `BOOTSTRAP_ADMIN_*` | creates your admin account once, on first start (registration only ever creates customers) |
   | `BOOTSTRAP_DEMO_*` | creates a public demo customer with an empty INR wallet (this password is public by design) |
   | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` | few DB connections; enough for the free plan |
   | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info` | hides the metrics endpoint from the internet |

4. **Create Web Service.** The first build takes 5 to 10 minutes (Maven downloads dependencies).
5. In the deploy log, look for:
   - `Successfully applied 3 migrations ... now at version v3`
   - `Bootstrap: created the admin account` and `Bootstrap: created the demo customer account`
   - the service turns **Live**
6. Check: `https://<backend-url>/actuator/health` shows `{"status":"UP",...}`.

---

## 3. Frontend: Render

1. **New + → Web Service** → the same repo:
   - Name: `wallet-ledger` (this becomes the public link)
   - Region: **Singapore** · Branch: `main`
   - Root Directory: **`web`** · Language: **Node** · Instance: **Free**
   - Build Command: **`npm ci && npm run build`**
   - Start Command: **`npm run start -- -p $PORT`**
2. **Environment variables** (Add from .env, same unsaved-tab method):
   ```
   NODE_VERSION=22
   API_BASE_URL=https://wallet-ledger-api-t6xu.onrender.com
   SESSION_SECRET=YOUR-SESSION-SECRET
   DEMO_PAYMENTS_ENABLED=true
   DEMO_LOGIN_EMAIL=demo@walletledger.dev
   DEMO_LOGIN_PASSWORD=try-the-demo-2026
   ```
   - `API_BASE_URL`: the backend URL, no trailing `/`.
   - `DEMO_LOGIN_*` must match the backend's `BOOTSTRAP_DEMO_*` exactly.
   - **Do not set** `MOCK_PROVIDER_ENABLED` (local development only).
   - Never put the admin login in `DEMO_LOGIN_*`; it would be shown on the public login page.
3. **Create Web Service** (3 to 6 minutes). The URL it shows is the public link.

---

## 4. Check it works

1. Open the live site. The login page shows **"Try it without signing up"**.
2. Click the demo account, sign in, then **Add money → Continue to payment → Pay** (labelled
   "Demo payment · simulated, no real money"). The status should reach "Payment confirmed by provider".
3. Register a second account and send money to it from the demo account.
4. Sign in as your admin: you land on **Overview**. Open **Reconciliation → Run now**: it should say
   **Passed, no findings**.
5. Optional: delete `BOOTSTRAP_ADMIN_PASSWORD` from the backend's Render environment. Your account
   already exists, and the bootstrap never changes an existing user.

Verified at launch (29 Oct 2026): demo sign-in, `HttpOnly` + `Secure` + `SameSite=Lax` session
cookie, a ₹5,000 demo deposit, admin sign-in, reconciliation passed, no browser errors.

---

## 5. Updating the live site later

1. Make changes locally, run the tests (below), commit, `git push`.
2. Render redeploys both services automatically from `main` (auto-deploy is on by default).
   If not: open the service → **Manual Deploy → Deploy latest commit**.
3. Database changes: add a new `backend/src/main/resources/db/migration/V<n>__name.sql`.
   Flyway applies it on the next backend start. **Never edit a migration that already ran.**
4. Changing an environment variable: service → **Environment** → edit → **Save, rebuild, and deploy**.

**Tests before pushing:**
```
cd backend && ./mvnw verify                       # 234 tests, coverage gate, regenerates openapi.json
cd web && npm run lint && npm run typecheck && npm test && npm run build
cd web && npm run test:e2e                        # needs the local backend running (see web/README.md)
```

---

## 6. Things to know about the free plan

- **Cold starts:** after ~15 minutes without visitors, both services sleep. The next visit takes
  about a minute to wake them. Say "free tier, first load may take a minute" when sharing the link.
- **Login is CPU-heavy:** passwords use Argon2 (deliberately slow). On a tenth of a CPU one login
  takes ~2 to 3 s; many simultaneous logins queue up and can time out. A paid instance fixes it.
- **Memory:** the backend fits in 512 MB. If the log ever shows `OutOfMemoryError`, see
  troubleshooting.
- **Backups:** Neon's free plan keeps a short restore window (see **Backup & Restore** in Neon).

---

## 7. Security housekeeping

- Rotate a secret if it was ever pasted into chat, a screenshot or a file:
  generate a new one → replace it in Render → redeploy.
  - `SESSION_SECRET` (frontend): everyone is signed out once; nothing else changes.
  - `JWT_SECRET` (backend): everyone must sign in again.
  - DB passwords: `ALTER ROLE wallet_app PASSWORD '...'` in Neon's SQL Editor, then update Render.
- The admin password can't be changed from the app yet (no "change password" screen).
- Keep only the demo customer's login public. Never make the demo account staff.
