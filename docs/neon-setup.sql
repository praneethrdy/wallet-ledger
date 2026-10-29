-- One-time database setup on Neon (or any managed Postgres 16 where you aren't superuser).
-- Run in Neon's SQL Editor, connected to database "neondb" as its default owner role.
-- First replace the two passwords (long random strings; you'll paste them into Render).
--
-- Same model as docker/postgres/init/01-roles.sh:
--   wallet_owner  owns the schema; Flyway uses it for migrations (DDL).
--   wallet_app    is what the running backend uses: rows only, no DDL, and (after
--                 migration V2) no UPDATE/DELETE on the ledger at all.

CREATE ROLE wallet_owner LOGIN PASSWORD 'REPLACE_WITH_OWNER_PASSWORD';
CREATE ROLE wallet_app   LOGIN PASSWORD 'REPLACE_WITH_APP_PASSWORD';

-- Lets this admin role hand the schema to wallet_owner and set its default privileges.
GRANT wallet_owner TO CURRENT_USER;

-- Extensions need a privileged role; migration V1's CREATE EXTENSION IF NOT EXISTS then no-ops.
CREATE EXTENSION IF NOT EXISTS citext;

ALTER SCHEMA public OWNER TO wallet_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO wallet_app;

-- Every table and sequence the migrations create is usable (rows only) by the app role.
ALTER DEFAULT PRIVILEGES FOR ROLE wallet_owner IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO wallet_app;
ALTER DEFAULT PRIVILEGES FOR ROLE wallet_owner IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO wallet_app;
