ALTER TABLE categories
    ADD COLUMN default_name VARCHAR(60) GENERATED ALWAYS AS (IF(user_id IS NULL, name, NULL)) VIRTUAL,
    ADD COLUMN default_name_si VARCHAR(60) GENERATED ALWAYS AS (IF(user_id IS NULL, name_si, NULL)) VIRTUAL,
    ADD CONSTRAINT uq_categories_default_name UNIQUE (kind, default_name),
    ADD CONSTRAINT uq_categories_default_name_si UNIQUE (kind, default_name_si);
