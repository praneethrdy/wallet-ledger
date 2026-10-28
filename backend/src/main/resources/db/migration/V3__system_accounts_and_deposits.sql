-- =====================================================================================
-- V3: system accounts per supported currency, and the deposits table.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- System accounts: the "other side" of money entering or leaving the platform.
--   EXTERNAL_FUNDING  - debited when a deposit credits a wallet (goes negative by design:
--                       its balance is minus the money customers have paid in)
--   REFUNDS_CLEARING  - holding account for refunds to external payment methods
--   FX_CLEARING       - the bridge between currencies in an FX conversion (Phase 3)
-- The set of currencies here IS the set of supported wallet currencies.
-- IDs are random v4 here (SQL has no v7); they are internal and never sorted on.
-- -------------------------------------------------------------------------------------
INSERT INTO accounts (id, owner_user_id, type, system_code, currency, name, status)
SELECT gen_random_uuid(), NULL, 'SYSTEM', code, currency,
       initcap(replace(lower(code), '_', ' ')) || ' (' || currency || ')', 'ACTIVE'
FROM unnest(ARRAY['EXTERNAL_FUNDING', 'REFUNDS_CLEARING', 'FX_CLEARING']) AS code
CROSS JOIN unnest(ARRAY['INR', 'USD', 'EUR']) AS currency;


-- -------------------------------------------------------------------------------------
-- deposits: a request to add money from an external payment provider.
-- PENDING until the provider's signed webhook says SUCCEEDED (ledger posted) or FAILED.
-- -------------------------------------------------------------------------------------
CREATE TABLE deposits (
    id                 UUID        PRIMARY KEY,
    wallet_id          UUID        NOT NULL REFERENCES accounts (id),
    amount             BIGINT      NOT NULL CHECK (amount > 0),
    currency           CHAR(3)     NOT NULL,
    provider_reference TEXT        NOT NULL UNIQUE,
    status             TEXT        NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    transaction_id     UUID        NULL REFERENCES transactions (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A deposit is linked to a ledger transaction exactly when it has succeeded.
    CONSTRAINT ck_deposits_transaction_iff_succeeded CHECK (
        (status = 'SUCCEEDED') = (transaction_id IS NOT NULL)
    ),
    CONSTRAINT fk_deposits_wallet_currency
        FOREIGN KEY (wallet_id, currency) REFERENCES accounts (id, currency)
);

-- One ledger transaction can settle at most one deposit.
CREATE UNIQUE INDEX ux_deposits_transaction ON deposits (transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX ix_deposits_wallet ON deposits (wallet_id, created_at);
CREATE INDEX ix_deposits_status_created ON deposits (status, created_at);

-- Admin search by status, support lookups by owner.
CREATE INDEX ix_accounts_status ON accounts (status) WHERE type = 'USER_WALLET';
