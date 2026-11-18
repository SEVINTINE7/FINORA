-- T03.6 Device tokens, SMS formats, support tickets, audit log
CREATE TABLE device_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(255) NOT NULL,
    platform   ENUM('ANDROID') NOT NULL DEFAULT 'ANDROID',
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_device_tokens_token UNIQUE (token),
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Reference data (D04): no user data, never real SMS text
CREATE TABLE sms_formats (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_code  VARCHAR(20) NOT NULL,
    version    INT         NOT NULL,
    pattern    TEXT        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_sms_formats_bank_version UNIQUE (bank_code, version)
);

CREATE TABLE support_tickets (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    staff_id   BIGINT       NULL,
    subject    VARCHAR(120) NOT NULL,
    body       TEXT         NOT NULL,
    status     ENUM('OPEN','IN_PROGRESS','CLOSED') NOT NULL DEFAULT 'OPEN',
    error_code VARCHAR(40)  NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_support_tickets_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_tickets_staff FOREIGN KEY (staff_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX ix_support_tickets_status (status)
);

CREATE TABLE ticket_replies (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id  BIGINT      NOT NULL,
    author_id  BIGINT      NULL,
    body       TEXT        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ticket_replies_ticket FOREIGN KEY (ticket_id) REFERENCES support_tickets (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_replies_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
);

-- Never store financial data or personal details here. Survives user deletion (T36).
CREATE TABLE audit_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id    BIGINT       NULL,
    action      VARCHAR(40)  NOT NULL,
    target_type VARCHAR(40)  NULL,
    target_id   BIGINT       NULL,  -- no FK: the target may be deleted
    reason      VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX ix_audit_events_created (created_at)
);
