ALTER TABLE sms_formats ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE sms_formats SET published = TRUE WHERE bank_code = 'NSB' AND version = 1;
ALTER TABLE sms_formats
    ADD COLUMN active_bank VARCHAR(20) GENERATED ALWAYS AS (IF(active, bank_code, NULL)) STORED,
    ADD CONSTRAINT uq_sms_formats_active_bank UNIQUE (active_bank),
    ADD CONSTRAINT ck_sms_formats_active_published CHECK (NOT active OR published);
