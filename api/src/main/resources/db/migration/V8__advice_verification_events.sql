-- T29: bounded outcomes only. Never persist provider text, prompts, credentials or personal financial records.
CREATE TABLE advice_verification_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    month CHAR(7) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    verified BOOLEAN NOT NULL,
    reason VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_advice_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_advice_verification_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    INDEX ix_advice_verification_user_month (user_id, month)
);
