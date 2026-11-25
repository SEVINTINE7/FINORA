CREATE TABLE consent_recovery_grants (
    user_id BIGINT PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_consent_recovery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
