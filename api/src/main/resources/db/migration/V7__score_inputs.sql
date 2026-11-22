-- T23 / D20: user declarations only. Financial transaction aggregates are always server-calculated.
CREATE TABLE score_inputs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    month CHAR(7) NOT NULL,
    emergency_reserve DECIMAL(15,2) NULL,
    reserve_as_of DATE NULL,
    no_applicable_debt BOOLEAN NULL,
    no_applicable_goals BOOLEAN NULL,
    confirmed_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_score_inputs_user_month UNIQUE (user_id, month),
    CONSTRAINT fk_score_inputs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_score_inputs_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_score_inputs_reserve CHECK (emergency_reserve IS NULL OR emergency_reserve >= 0),
    CONSTRAINT ck_score_inputs_reserve_date CHECK (
        (emergency_reserve IS NULL AND reserve_as_of IS NULL) OR
        (emergency_reserve IS NOT NULL AND reserve_as_of IS NOT NULL AND DATE_FORMAT(reserve_as_of, '%Y-%m') = month))
);
