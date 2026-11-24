ALTER TABLE health_scores MODIFY COLUMN total TINYINT NULL;

CREATE TABLE monthly_history (
    user_id BIGINT NOT NULL,
    month CHAR(7) NOT NULL,
    income DECIMAL(30,2) NOT NULL,
    expense DECIMAL(30,2) NOT NULL,
    score TINYINT NULL,
    insights JSON NOT NULL,
    dirty BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, month),
    INDEX ix_monthly_history_dirty (dirty, user_id, month),
    CONSTRAINT fk_monthly_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_monthly_history_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_monthly_history_score CHECK (score BETWEEN 0 AND 100)
);
