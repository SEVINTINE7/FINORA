-- T34 Budget, bill and goal alerts (D57)
-- Per-type opt-outs; every user starts with all three alert types on.
ALTER TABLE users
    ADD COLUMN alert_budget BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN alert_bill   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN alert_goal   BOOLEAN NOT NULL DEFAULT TRUE;

-- One row per alert event. The unique key makes the daily job safe to repeat: the same event
-- (user, type, budget/bill/goal id, period) is stored once and therefore sent at most once.
-- No amounts, names or text are stored; the app renders the message from the type.
CREATE TABLE alert_events (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       ENUM('BUDGET_NEAR','BUDGET_OVER','BILL_DUE','GOAL_DATE_NEAR') NOT NULL,
    ref_id     BIGINT       NOT NULL,
    period     VARCHAR(10)  NOT NULL,  -- YYYY-MM for budgets/bills, YYYY-MM-DD target date for goals
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at    DATETIME(6)  NULL,
    CONSTRAINT uq_alert_events_event UNIQUE (user_id, type, ref_id, period),
    CONSTRAINT fk_alert_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX ix_alert_events_unsent ON alert_events (sent_at, created_at);
