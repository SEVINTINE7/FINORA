CREATE TABLE support_reply_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic ENUM('SIGN_IN','NAVIGATION','SMS_PERMISSION','LANGUAGE') NOT NULL,
    english VARCHAR(600) NOT NULL,
    sinhala VARCHAR(600) NOT NULL,
    draft_by BIGINT NULL,
    reviewed_by BIGINT NULL,
    state ENUM('DRAFT','ACTIVE','RETIRED') NOT NULL DEFAULT 'DRAFT',
    active_topic VARCHAR(20) GENERATED ALWAYS AS (IF(state = 'ACTIVE', CAST(topic AS CHAR), NULL)) STORED,
    CONSTRAINT fk_support_reply_drafter FOREIGN KEY (draft_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_support_reply_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL,
    UNIQUE KEY uq_support_reply_active_topic (active_topic)
);
