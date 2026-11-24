ALTER TABLE monthly_history ADD COLUMN reconstructed_at DATETIME(6) NULL;

CREATE TABLE history_recovery_policy (
    id TINYINT PRIMARY KEY CHECK (id = 1),
    first_month CHAR(7) NOT NULL
);

CREATE TABLE history_recovery_retry (
    user_id BIGINT NOT NULL,
    month CHAR(7) NOT NULL,
    retry_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, month),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
