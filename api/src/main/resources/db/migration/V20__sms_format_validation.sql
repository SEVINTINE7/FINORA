ALTER TABLE sms_formats
    MODIFY COLUMN pattern MEDIUMTEXT NOT NULL,
    ADD COLUMN validated_definition CHAR(64) NULL,
    ADD COLUMN validated_fixtures CHAR(64) NULL;
