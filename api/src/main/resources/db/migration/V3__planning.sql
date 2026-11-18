-- T03.4 Budgets, bills, goals
CREATE TABLE budgets (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    category_id  BIGINT        NOT NULL,
    month        CHAR(7)       NOT NULL,  -- YYYY-MM
    limit_amount DECIMAL(15,2) NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_budgets_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_budgets_limit_positive CHECK (limit_amount > 0),
    CONSTRAINT uq_budgets_user_category_month UNIQUE (user_id, category_id, month),
    CONSTRAINT fk_budgets_user     FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_budgets_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
);

CREATE TABLE bills (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    name        VARCHAR(60)   NOT NULL,
    amount      DECIMAL(15,2) NOT NULL,
    due_day     TINYINT       NOT NULL,  -- 1-31; 31 in short months = last day (T18)
    category_id BIGINT        NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_bills_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_bills_due_day CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT fk_bills_user     FOREIGN KEY (user_id)     REFERENCES users (id)      ON DELETE CASCADE,
    CONSTRAINT fk_bills_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL
);

CREATE TABLE bill_payments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_id    BIGINT      NOT NULL,
    month      CHAR(7)     NOT NULL,
    paid_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_bill_payments_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT uq_bill_payments_bill_month UNIQUE (bill_id, month),
    CONSTRAINT fk_bill_payments_bill FOREIGN KEY (bill_id) REFERENCES bills (id) ON DELETE CASCADE
);

CREATE TABLE goals (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    name          VARCHAR(60)   NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    target_date   DATE          NULL,
    status        ENUM('ACTIVE','DONE','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_goals_target_positive CHECK (target_amount > 0),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE goal_contributions (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    goal_id      BIGINT        NOT NULL,
    amount       DECIMAL(15,2) NOT NULL,
    contrib_date DATE          NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_goal_contributions_amount_positive CHECK (amount > 0),
    CONSTRAINT fk_goal_contributions_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE
);
