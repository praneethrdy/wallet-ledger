-- =====================================================================================
-- V2: the ledger.
--
-- Tests that define "done" (under backend/src/test/java/com/example/wallet/):
--   SchemaIT, ledger/LedgerAppendOnlyIT, ledger/DoubleEntryTriggerIT
--
-- Three layers protect the ledger, each catching what the others might miss:
--   1. LedgerService (Java, Step 5) validates before inserting.
--   2. Triggers: rows are append-only, and every transaction must balance at COMMIT.
--   3. Privileges: the app role may only SELECT and INSERT ledger rows.
--
-- `${app_role}` is a Flyway placeholder for the app's DB role (wallet_app).
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- transactions: the header of one money movement. The legs live in ledger_entries.
-- -------------------------------------------------------------------------------------
CREATE TABLE transactions (
    id                         UUID        PRIMARY KEY,
    type                       TEXT        NOT NULL
                               CHECK (type IN ('DEPOSIT', 'TRANSFER', 'REFUND', 'REVERSAL', 'FX_CONVERSION')),
    reference                  TEXT        NOT NULL UNIQUE,
    description                TEXT        NULL,
    reversal_of_transaction_id UUID        NULL REFERENCES transactions (id),
    initiated_by_user_id       UUID        NOT NULL REFERENCES users (id),
    -- Soft reference, deliberately NOT a foreign key: see ADR-004.
    idempotency_key_id         UUID        NULL,
    metadata                   JSONB       NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A REVERSAL always points at what it reverses, and nothing else may point anywhere.
    CONSTRAINT ck_transactions_reversal_shape CHECK (
        (type = 'REVERSAL') = (reversal_of_transaction_id IS NOT NULL)
    ),
    CONSTRAINT ck_transactions_not_self_reversal CHECK (reversal_of_transaction_id <> id)
);

-- A transaction can be reversed at most once. A plain UNIQUE would also work (Postgres
-- treats NULLs as distinct), but partial indexes only the reversal rows: the index stays
-- tiny instead of holding a NULL for every non-reversal transaction.
CREATE UNIQUE INDEX ux_transactions_reversal_of
    ON transactions (reversal_of_transaction_id)
    WHERE reversal_of_transaction_id IS NOT NULL;

CREATE INDEX ix_transactions_created ON transactions (created_at, id);


-- -------------------------------------------------------------------------------------
-- ledger_entries: the legs. Amount is always positive; the sign lives in `direction`.
-- -------------------------------------------------------------------------------------

-- Composite FK target: lets the DB guarantee a leg's currency equals its account's currency.
ALTER TABLE accounts ADD CONSTRAINT ux_accounts_id_currency UNIQUE (id, currency);

CREATE TABLE ledger_entries (
    id             BIGSERIAL   PRIMARY KEY,  -- internal only; total order for keyset pagination
    transaction_id UUID        NOT NULL REFERENCES transactions (id),
    account_id     UUID        NOT NULL,
    direction      TEXT        NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount         BIGINT      NOT NULL CHECK (amount > 0),
    currency       CHAR(3)     NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- An INR leg on a USD account is rejected on INSERT, not discovered at reconciliation.
    CONSTRAINT fk_ledger_entries_account_currency
        FOREIGN KEY (account_id, currency) REFERENCES accounts (id, currency)
);

-- Statement / entries page / balance for one account, newest first.
CREATE INDEX ix_ledger_entries_account_created ON ledger_entries (account_id, created_at, id);
-- All legs of one transaction (detail view, and the balance trigger below).
CREATE INDEX ix_ledger_entries_transaction ON ledger_entries (transaction_id);


-- -------------------------------------------------------------------------------------
-- Append-only: history is never rewritten. Corrections are new, reversing transactions.
-- -------------------------------------------------------------------------------------
CREATE FUNCTION reject_ledger_mutation() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public
AS $$
BEGIN
    RAISE EXCEPTION 'ledger is append-only: % on % is not allowed', TG_OP, TG_TABLE_NAME
        USING HINT = 'Post a reversing transaction instead.';
END
$$;

CREATE TRIGGER trg_transactions_append_only
    BEFORE UPDATE OR DELETE ON transactions
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_mutation();

-- Row-level triggers do not fire on TRUNCATE, which would wipe the ledger in one statement.
CREATE TRIGGER trg_transactions_no_truncate
    BEFORE TRUNCATE ON transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_no_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_mutation();


-- -------------------------------------------------------------------------------------
-- Double-entry, checked at COMMIT.
--
-- Deferred because a transfer inserts its DEBIT and its CREDIT in two statements, and the
-- transaction is legitimately unbalanced in between. At COMMIT every leg is visible, so
-- that is the moment to check. A failure here rolls back the whole DB transaction.
--
-- The function fires once per inserted leg (and once per header), so a 2-leg transfer is
-- checked 3 times. That is a few index lookups on ix_ledger_entries_transaction, which is
-- cheap, and it keeps the trigger simple and obviously correct.
-- -------------------------------------------------------------------------------------
CREATE FUNCTION assert_transaction_balanced() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public
AS $$
DECLARE
    tx_id         UUID;
    has_debit     BOOLEAN;
    has_credit    BOOLEAN;
    bad_currency  CHAR(3);
    bad_net       BIGINT;
BEGIN
    -- Fired from two tables. IF, not CASE: plpgsql resolves NEW.<field> even in a CASE
    -- branch that never runs, and transactions has no transaction_id column.
    IF TG_TABLE_NAME = 'transactions' THEN
        tx_id := NEW.id;
    ELSE
        tx_id := NEW.transaction_id;
    END IF;

    SELECT coalesce(bool_or(direction = 'DEBIT'), false),
           coalesce(bool_or(direction = 'CREDIT'), false)
      INTO has_debit, has_credit
      FROM ledger_entries
     WHERE transaction_id = tx_id;

    IF NOT (has_debit AND has_credit) THEN
        RAISE EXCEPTION 'transaction % is unbalanced: it needs at least one DEBIT and one CREDIT', tx_id
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT currency, sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END)
      INTO bad_currency, bad_net
      FROM ledger_entries
     WHERE transaction_id = tx_id
     GROUP BY currency
    HAVING sum(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END) <> 0
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'transaction % is unbalanced in %: debits - credits = %', tx_id, bad_currency, bad_net
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL; -- return value of an AFTER trigger is ignored
END
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_entries_balanced
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_transaction_balanced();

-- Catches a header inserted with ZERO legs: the trigger above never fires in that case.
CREATE CONSTRAINT TRIGGER trg_transactions_balanced
    AFTER INSERT ON transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_transaction_balanced();


-- -------------------------------------------------------------------------------------
-- Least privilege: V1's default privileges gave the app full DML. For the ledger, it only
-- ever needs to read and append. Even if the trigger were dropped, the app couldn't mutate.
-- -------------------------------------------------------------------------------------
REVOKE UPDATE, DELETE, TRUNCATE ON transactions, ledger_entries FROM ${app_role};
