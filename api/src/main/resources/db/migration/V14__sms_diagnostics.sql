CREATE TABLE sms_diagnostic_reports (
    user_id BIGINT NOT NULL,
    bank_code VARCHAR(20) NOT NULL,
    format_version INT NOT NULL,
    error_code VARCHAR(40) NOT NULL,
    report_day DATE NOT NULL,
    PRIMARY KEY (user_id, bank_code, format_version, error_code, report_day),
    CONSTRAINT fk_sms_diagnostic_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_sms_diagnostic_group (bank_code, error_code, format_version)
);

CREATE TABLE sms_diagnostic_escalations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_code VARCHAR(20) NOT NULL,
    error_code VARCHAR(40) NOT NULL,
    status ENUM('OPEN', 'ACKNOWLEDGED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    active_bank VARCHAR(20) GENERATED ALWAYS AS (IF(status = 'CLOSED', NULL, bank_code)) STORED,
    UNIQUE KEY uq_sms_diagnostic_escalation (active_bank, error_code)
);
