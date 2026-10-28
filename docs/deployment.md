# Deployment

## Shape

- **Stateless app.** Any number of identical backend containers can run behind a load balancer, with no sticky sessions (JWTs carry identity; ADR-008).
- **One managed PostgreSQL 16 instance** (e.g. AWS RDS, Cloud SQL, Azure Flexible Server) with automated backups, point-in-time recovery and a standby replica. All state lives here.
- **Image:** `backend/Dockerfile` is a multi-stage build that ends with a JRE-only, non-root image with a healthcheck on `/actuator/health/liveness`.

## Database

1. Create the database and the two roles exactly as `docker/postgres/init/01-roles.sh` does:
   - `wallet_owner`, used only by Flyway, owns the schema;
   - `wallet_app`, the runtime role, gets DML via default privileges.
   On managed Postgres, run that SQL once as the admin user.
2. The app runs Flyway on startup using the **owner** credentials, then connects as **wallet_app**. To keep DDL rights out of the running app entirely, run migrations as a separate deploy step (`flyway migrate` or a one-off container) and set `spring.flyway.enabled=false` on the app.
3. Never edit an applied migration. Fix forward with a new `V<n>__...sql`.
4. Use TLS to the database (`?sslmode=verify-full` on `DB_URL`).

## Configuration and secrets

| Variable | Notes |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | JSON logs; no dev seed; no Swagger UI; no defaults for secrets (startup fails if one is missing) |
| `DB_URL`, `DB_APP_USER`/`DB_APP_PASSWORD`, `DB_OWNER_USER`/`DB_OWNER_PASSWORD` | from the secrets manager |
| `JWT_SECRET` | base64, ≥ 32 random bytes (`openssl rand -base64 32`) |
| `WEBHOOK_SECRET`, `WEBHOOK_PREVIOUS_SECRET` | provider signing secrets; set the previous one only during rotation |

Inject secrets from a secrets manager (AWS Secrets Manager or SSM, GCP Secret Manager, Vault, Kubernetes Secrets backed by one), never from the image or the repo.

**Rotating the webhook secret:**
1. Set `WEBHOOK_PREVIOUS_SECRET` = the current secret and `WEBHOOK_SECRET` = the new one, then deploy.
2. Switch the provider to the new secret.
3. Once no old signatures arrive, clear `WEBHOOK_PREVIOUS_SECRET`.

**Rotating the JWT secret** logs everyone out of access tokens at once (at most 15 min of impact, since refresh tokens still work). Supporting two JWT keys via a `kid` header is future work.

## Scheduled jobs: ShedLock

The nightly reconciliation (02:00 IST) and the idempotency-key cleanup are `@Scheduled` on **every** instance. ShedLock makes the instances race for a row in the `shedlock` table, using database time (`usingDbTime()`) so clock skew between hosts doesn't matter. Exactly one instance runs each job:
- `lockAtMostFor` releases the lock if the winner dies mid-run;
- `lockAtLeastFor` stops a second node from re-running a job that finished very quickly.

Running the jobs as a separate worker deployment (scheduling enabled there, `app.scheduling.enabled=false` on the API pods) is an option when the API autoscales aggressively.

## Network and edge

- **TLS at the load balancer.** `server.forward-headers-strategy=native` (prod) trusts `X-Forwarded-For`/`-Proto`, which is only safe if the app is reachable *only* through that proxy. Otherwise clients can spoof their IP in the audit log.
- **`/actuator/prometheus`** is unauthenticated for scraping. Keep it off the public internet: block `/actuator/**` at the load balancer except `health`, or move management to a separate port with `management.server.port`.
- **Webhook endpoint** `/api/v1/webhooks/payment-provider` must be publicly reachable by the provider. If the provider publishes source IP ranges, allow-list them as defence in depth on top of the HMAC.
- **Request body limit:** 64 KiB (`app.http.max-request-bytes`). Set a matching limit on the load balancer.

## Observability

- **Logs:** one JSON object per line on stdout, including `correlationId`; ship them with the platform's agent.
- **Metrics** (Prometheus):
  - `http_server_requests_seconds`
  - `wallet_transfers_total{outcome}`
  - `wallet_idempotency_replays_total`
  - `wallet_webhooks_total{outcome}`
  - `wallet_ledger_lock_wait_seconds`
  - `ledger_reconciliation_duration_seconds`
  - `ledger_reconciliation_mismatches_total`
- **Alerts worth having:**
  - `increase(ledger_reconciliation_mismatches_total[1d]) > 0` (page someone);
  - a webhook failure ratio above 5%;
  - p99 lock wait above 1s;
  - a 5xx rate above 1%.

## Rollout

Migrations must be backward-compatible with the previous app version (expand, then contract), because old and new pods run side by side during a rolling deploy. Readiness (`/actuator/health/readiness`) gates traffic until the app has started and connected to the database.
