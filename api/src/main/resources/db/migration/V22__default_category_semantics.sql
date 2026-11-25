-- V6 was a single ordered insert into the V2 table. Prove both original rows
-- belong to that seed cohort before assigning financial meaning. Display names and
-- active flags may already have changed. Unsupported seed layouts fail before DDL.
CREATE TEMPORARY TABLE v22_category_seed_guard (
    verified BOOLEAN NOT NULL,
    CONSTRAINT ck_v22_original_v6_seed_identity CHECK (verified = TRUE)
);
INSERT INTO v22_category_seed_guard (verified)
SELECT EXISTS (
    SELECT 1
    FROM categories loan
    JOIN categories savings ON savings.id = 16 AND savings.created_at = loan.created_at
    WHERE loan.id = 15 AND loan.user_id IS NULL AND loan.kind = 'EXPENSE' AND loan.need_or_want = 'NEED'
      AND savings.user_id IS NULL AND savings.kind = 'EXPENSE' AND savings.need_or_want IS NULL
      AND (SELECT COUNT(*) FROM categories witness
           WHERE witness.id BETWEEN 1 AND 4 AND witness.kind = 'INCOME'
             AND witness.user_id IS NULL AND witness.created_at = loan.created_at) >= 2
      AND (SELECT COUNT(*) FROM categories witness
           WHERE witness.id BETWEEN 5 AND 14 AND witness.kind = 'EXPENSE'
             AND witness.user_id IS NULL AND witness.created_at = loan.created_at) >= 2
);
DROP TEMPORARY TABLE v22_category_seed_guard;

ALTER TABLE categories
    ADD COLUMN semantic_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uq_categories_semantic_code UNIQUE (semantic_code),
    ADD CONSTRAINT ck_categories_semantic_scope CHECK (
        semantic_code IS NULL OR (
            user_id IS NULL AND kind = 'EXPENSE' AND semantic_code IN ('SAVINGS', 'LOAN_PAYMENT')
        )
    );

UPDATE categories
SET semantic_code = CASE id WHEN 15 THEN 'LOAN_PAYMENT' WHEN 16 THEN 'SAVINGS' END
WHERE id IN (15, 16);
