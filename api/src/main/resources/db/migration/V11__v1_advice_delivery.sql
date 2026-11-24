ALTER TABLE advice
    ADD COLUMN monthly_delivery BOOLEAN NULL,
    ADD CONSTRAINT ck_advice_monthly_delivery CHECK (monthly_delivery IS NULL OR (monthly_delivery = TRUE AND mode = 'V1')),
    ADD CONSTRAINT uq_advice_monthly_delivery UNIQUE (user_id, month, monthly_delivery);
