-- T03.5 Insights, health scores, advice, ratings
CREATE TABLE insights (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    month      CHAR(7)     NOT NULL,
    type       VARCHAR(40) NOT NULL,
    facts      JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_insights_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT fk_insights_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_insights_user_month (user_id, month)
);

CREATE TABLE health_scores (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    month      CHAR(7)     NOT NULL,
    total      TINYINT     NOT NULL,
    parts      JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_health_scores_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_health_scores_total CHECK (total BETWEEN 0 AND 100),
    CONSTRAINT uq_health_scores_user_month UNIQUE (user_id, month),
    CONSTRAINT fk_health_scores_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE advice (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    month      CHAR(7)     NOT NULL,
    source     ENUM('RULE','AI') NOT NULL,
    mode       ENUM('BETA','V1') NOT NULL,  -- D03
    body       JSON        NOT NULL,
    verified   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_advice_month CHECK (month REGEXP '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT fk_advice_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_advice_user_month (user_id, month)
);

CREATE TABLE advice_ratings (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    advice_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    rating     TINYINT     NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_advice_ratings_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_advice_ratings_advice_user UNIQUE (advice_id, user_id),
    CONSTRAINT fk_advice_ratings_advice FOREIGN KEY (advice_id) REFERENCES advice (id) ON DELETE CASCADE,
    CONSTRAINT fk_advice_ratings_user   FOREIGN KEY (user_id)   REFERENCES users (id)  ON DELETE CASCADE
);
