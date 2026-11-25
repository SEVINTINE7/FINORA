ALTER TABLE users ADD COLUMN staff_session_generation BIGINT NOT NULL DEFAULT 0;
ALTER TABLE refresh_tokens ADD COLUMN staff_session_generation BIGINT NOT NULL DEFAULT 0;

CREATE TABLE staff_activations (
    staff_id BIGINT PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_staff_activation_user FOREIGN KEY (staff_id) REFERENCES users (id) ON DELETE CASCADE
);
