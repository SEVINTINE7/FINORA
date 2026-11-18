# FINORA — Decisions

Short, numbered, referenced by tasks (e.g. "see D03"). Add new ones at the bottom of the right list.

## Decided

- **D01 Roles** — USER, ADMIN, STAFF approved by the lecturer (Nov 2026). Admin has 5 functions (T41–T45), Staff has 4 (T46–T49). Admin/Staff use the same Android app, routed by role after login.
- **D02 Repository** — one repo: `/api`, `/android`, `/docs`.
- **D03 Advice versions** — `beta` build: rule-based and AI advice shown side by side, unlabeled, random order, user rates both 1–5 (blind experiment). `v1` build: AI advice if it passes the number check, otherwise rule-based. Implemented as Android flavors; the app sends `mode=BETA|V1` with advice requests. No runtime switch.
- **D04 Reference data (FR12)** — means only default categories + bank SMS formats.
- **D05 Languages** — Sinhala and English. Tamil later, not in scope.
- **D06 Currency** — LKR only; amounts `DECIMAL(15,2)`.

## Proposed — confirm in T01

- **P01 Tokens** — JWT access token 30 min; refresh token 14 days, rotated on every refresh, stored as SHA-256 hash; logout revokes it.
- **P02 Login rate limit** — 5 failed attempts per email in 15 min → HTTP 429 for 15 min.
- **P03 Passwords** — BCrypt cost 12; minimum 8 chars with at least one letter and one digit.
- **P04 Pattern thresholds** — wants > 40% of monthly income (from proposal); payday window = first 3 days after an income record; weekend = Sat–Sun; high fixed bills = bills > 50% of income.
- **P05 Health score weights (total 100)** — savings 25, spending control 25, debt payments 15, emergency savings 20, goal progress 15. Thilina to define each part's formula.
- **P06 Test database** — Testcontainers MySQL 8 if Docker is available; otherwise local schema `finora_test`.
- **P07 Consent version** — `"1.0"`; bump when consent text changes.
- **P08 Git workflow** — branch per task `t09-accounts-categories`, PR to `main`, one reviewer from the team.
