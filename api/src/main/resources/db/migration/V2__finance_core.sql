-- T03.3 Accounts, categories, transactions
CREATE TABLE accounts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    name            VARCHAR(60)   NOT NULL,
    type            ENUM('BANK','CASH','CARD','WALLET') NOT NULL,
    opening_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    archived        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_accounts_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- user_id NULL = default category managed by Admin (T44)
CREATE TABLE categories (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NULL,
    name         VARCHAR(60) NOT NULL,
    name_si      VARCHAR(60) NULL,
    kind         ENUM('INCOME','EXPENSE') NOT NULL,
    need_or_want ENUM('NEED','WANT')      NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_categories_user_kind_name UNIQUE (user_id, kind, name),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE transactions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    account_id  BIGINT        NOT NULL,
    category_id BIGINT        NULL,
    type        ENUM('INCOME','EXPENSE') NOT NULL,
    amount      DECIMAL(15,2) NOT NULL,
    txn_date    DATE          NOT NULL,
    merchant    VARCHAR(100)  NULL,
    note        VARCHAR(255)  NULL,
    source      ENUM('MANUAL','SMS','PASTE','CSV') NOT NULL DEFAULT 'MANUAL',
    dedup_hash  CHAR(64)      NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT uq_transactions_user_dedup UNIQUE (user_id, dedup_hash),
    CONSTRAINT fk_transactions_user     FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    -- API blocks deleting accounts in use (archive instead, T09); cascade keeps user deletion reliable
    CONSTRAINT fk_transactions_account  FOREIGN KEY (account_id)  REFERENCES accounts (id)   ON DELETE CASCADE,
    -- deleting a category never deletes money records
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    INDEX ix_transactions_user_date (user_id, txn_date)
);
