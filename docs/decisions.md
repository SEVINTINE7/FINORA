# FINORA — Decisions

Short, numbered, referenced by tasks (e.g. "see D03"). Add new ones at the bottom of the right list.

## Decided

- **D01 Roles** — USER, ADMIN, STAFF approved by the lecturer (Nov 2026). Admin has 5 functions (T41–T45), Staff has 4 (T46–T49). Admin/Staff use the same Android app, routed by role after login.
- **D02 Repository** — one repo: `/api`, `/android`, `/docs`.
- **D03 Advice versions** — `beta` build: rule-based and AI advice shown side by side, unlabeled, random order, user rates both 1–5 (blind experiment). `v1` build: AI advice if it passes the number check, otherwise rule-based. Implemented as Android flavors; the app sends `mode=BETA|V1` with advice requests. No runtime switch.
- **D04 Reference data (FR12)** — means only default categories + bank SMS formats.
- **D05 Languages** — Sinhala and English. Tamil later, not in scope.
- **D06 Currency** — LKR only; amounts `DECIMAL(15,2)`.
- **D07 Tokens** (was P01) — JWT access token 30 min; refresh token 14 days, rotated on every refresh, stored as SHA-256 hash; logout revokes it.
- **D08 Login rate limit** (was P02) — **removed**: no login-attempt limiting in FINORA.
- **D09 Passwords** (was P03) — BCrypt cost 12; minimum 8 chars with at least one letter and one digit.
- **D10 Pattern thresholds** (was P04) — wants > 40% of monthly income; payday window = first 3 days after an income record; weekend = Sat–Sun; high fixed bills = bills > 50% of income.
- **D11 Test database** (was P06) — local MySQL 8 schema `finora_test` on every machine (no Docker/Testcontainers). Test profile connects via `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASS`.
- **D12 Consent version** (was P07) — `"1.0"`; bump when consent text changes.
- **D13 Git workflow** (was P08) — everyone commits directly to `main`. Always `git pull --rebase` before starting a task and before pushing; push small commits often; never force-push `main`. Commit message starts with the task ID, e.g. `T09.2: categories CRUD`.
- **D14 Spring Boot version** — Spring Boot 4.1.x (4.1.2 at T02). Use Boot 4 starter names (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `*-test` starters). Local dev/test DB user `finora`/`finora` via `api/scripts/create-local-db.sql` (local only).
- **D15 Withdrawn consent (T07.3)** — after withdrawal, `GET /api/v1/me` returns `consentVersion: null`; the app checks it after login and offers re-consent or data deletion. No re-consent endpoint for now (add later / with T36 if needed). Login response unchanged.
- **D16 Savings goals (T19)** — target date (optional) must be after today on create **and** on every edit. Status is automatic: `DONE` when saved ≥ target, back to `ACTIVE` if it drops below (new target or deleted contribution); the user can only cancel (`cancelled: true` on PUT). Contributions are add/delete only, no edit.

## Proposed — still open

- **P05 Health score (owner: Thilina, needed by T23)** — Thilina sets the five part weights (savings, spending control, debt payments, emergency savings, goal progress; total 100) **and** each part's formula. Starting suggestion: 25 / 25 / 15 / 20 / 15.
