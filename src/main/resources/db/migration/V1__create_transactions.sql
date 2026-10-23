CREATE TABLE transactions (
    id                      UUID                     PRIMARY KEY,
    account_id              VARCHAR(64)              NOT NULL,
    type                    VARCHAR(16)              NOT NULL,
    amount                  NUMERIC(19, 2)           NOT NULL,
    currency                VARCHAR(3)               NOT NULL,
    description             VARCHAR(255),
    status                  VARCHAR(16)              NOT NULL,
    provider_transaction_id VARCHAR(64),
    balance_after           NUMERIC(19, 2),
    error_code              VARCHAR(64),
    error_message           VARCHAR(512),
    idempotency_key         VARCHAR(128),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key)
);

-- Serves GET /transactions?accountId=... (newest first) and the account+status/type filters
CREATE INDEX idx_transactions_account_created ON transactions (account_id, created_at DESC);
-- Serves GET /transactions?status=... (e.g. reconciliation of PENDING/FAILED)
CREATE INDEX idx_transactions_status_created ON transactions (status, created_at DESC);
