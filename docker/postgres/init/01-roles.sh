#!/bin/sh
# Runs once, on first container start (empty data dir), as the superuser.
# Also reused by the Testcontainers base test so tests run with the same roles as dev/prod.
#
# WHY two roles:
#   - wallet_owner owns the schema; Flyway uses it for DDL.
#   - wallet_app is what the running service uses. It can read/write rows but cannot
#     CREATE/ALTER/DROP anything, and (after V2) cannot UPDATE/DELETE ledger rows at all.
#   A SQL-injection bug or a buggy repository method in the app therefore cannot drop
#   tables or rewrite history — the database itself refuses.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v owner_user="$DB_OWNER_USER" -v owner_pw="$DB_OWNER_PASSWORD" \
  -v app_user="$DB_APP_USER" -v app_pw="$DB_APP_PASSWORD" <<'EOSQL'
CREATE ROLE :"owner_user" LOGIN PASSWORD :'owner_pw';
CREATE ROLE :"app_user"   LOGIN PASSWORD :'app_pw';

REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"owner_user", :"app_user";

-- Owner of the database so it can CREATE EXTENSION citext (a "trusted" extension).
ALTER DATABASE :"db" OWNER TO :"owner_user";
ALTER SCHEMA public OWNER TO :"owner_user";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app_user";

-- Every table/sequence the owner creates later is automatically usable (DML only) by the app.
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner_user" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner_user" IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
EOSQL
