-- T61/D64: only a BCrypt hash of the current reset code is stored.
CREATE TABLE password_reset_codes (
    user_id BIGINT PRIMARY KEY,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    session_generation BIGINT NOT NULL,
    CONSTRAINT fk_reset_code_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_reset_code_attempts CHECK (attempts BETWEEN 0 AND 4)
);

-- Keep issuance history separate: success or exhaustion must not reset the hourly quota.
CREATE TABLE password_reset_issuances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_reset_issuance_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX ix_reset_issuance_user_time (user_id, issued_at)
);
