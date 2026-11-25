-- D58: monthly advice is stored per month AND per user-selected focus goal (0 = no goal).
-- No foreign key on focus_goal_id: deleting a goal must not touch stored advice or collide with the no-goal row.
ALTER TABLE advice
    ADD COLUMN focus_goal_id BIGINT NULL,
    ADD COLUMN focus_key BIGINT AS (COALESCE(focus_goal_id, 0)) STORED;
ALTER TABLE advice
    DROP INDEX uq_advice_monthly_delivery,
    ADD CONSTRAINT uq_advice_monthly_delivery UNIQUE (user_id, month, focus_key, monthly_delivery);

ALTER TABLE advice_generation_leases
    ADD COLUMN focus_key BIGINT NOT NULL DEFAULT 0,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id, month, focus_key);
