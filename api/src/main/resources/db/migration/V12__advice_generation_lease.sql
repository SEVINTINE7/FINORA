CREATE TABLE advice_generation_leases (
    user_id BIGINT NOT NULL,
    month CHAR(7) NOT NULL,
    lease_token CHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, month),
    CONSTRAINT fk_advice_generation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
