# FINORA — Decisions

Short, numbered, referenced by tasks (e.g. "see D03"). Add new ones at the bottom of the right list.

## Decided

- **D01 Roles** — USER, ADMIN, STAFF approved by the lecturer (Nov 2026). Admin has 5 functions (T41–T45), Staff has 4 (T46–T49). Admin/Staff use the same Android app, routed by role after login.
- **D02 Repository** — one repo: `/api`, `/android`, `/docs`.
- **D03 Advice versions** — `v1` build: AI advice if it passes the number check, otherwise rule-based. Implemented as Android flavors; the app sends `mode=V1` with advice requests. No runtime switch.
- **D04 Reference data (FR12)** — means only default categories + bank SMS formats.
- **D05 Languages** — Sinhala and English. Tamil later, not in scope.
- **D06 Currency** — LKR only; amounts `DECIMAL(15,2)`.
- **D07 Tokens** (was P01) — JWT access token 30 min; refresh token 14 days, rotated on every refresh, stored as SHA-256 hash; logout revokes it.
  - **T08 Android refresh failure policy** (Sevindu's verbal approval relayed by Sahan, 2026-11-22) — clear the local session and return to login on definitive rejection (`401 INVALID_REFRESH_TOKEN`), `403 ACCOUNT_DISABLED`, or a parsed successful response with missing or unsupported session data. Preserve the current session for retry on transport failures, server errors, unrecognized error responses, or responses that cannot be parsed. A protected call retries at most once after successful rotation.
- **D08 Login rate limit** (was P02) — **removed**: no login-attempt limiting in FINORA.
- **D09 Passwords** (was P03) — BCrypt cost 12; minimum 8 chars with at least one letter and one digit.
- **D10 Pattern thresholds** (was P04) — wants > 40% of monthly income; payday window = first 3 days after an income record; weekend = Sat–Sun; high fixed bills = bills > 50% of income.
- **D11 Test database** (was P06) — local MySQL 8 schema `finora_test` on every machine (no Docker/Testcontainers). Test profile connects via `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASS`.
- **D12 Consent version** (was P07) — `"1.0"`; bump when consent text changes.
- **D13 Git workflow** (was P08) — everyone commits directly to `main`. Always `git pull --rebase` before starting a task and before pushing; push small commits often; never force-push `main`. Commit message starts with the task ID, e.g. `T09.2: categories CRUD`.
- **D14 Spring Boot version** — Spring Boot 4.1.x (4.1.2 at T02). Use Boot 4 starter names (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `*-test` starters). Local dev/test DB user `finora`/`finora` via `api/scripts/create-local-db.sql` (local only).
- **D15 Withdrawn consent (T07.3)** — after withdrawal, `GET /api/v1/me` returns `consentVersion: null`; the app checks it after login and offers re-consent or data deletion. No re-consent endpoint for now (add later / with T36 if needed). Login response unchanged.
- **D16 Savings goals (T19)** — target date (optional) must be after today on create **and** on every edit. Status is automatic: `DONE` when saved ≥ target, back to `ACTIVE` if it drops below (new target or deleted contribution); the user can only cancel (`cancelled: true` on PUT). Contributions are add/delete only, no edit.
- **D17 Dashboard (T20)** — pie (`byCategory`) shows expenses only, uncategorised as one slice. `month` is optional on `GET /dashboard`; missing = current month in Asia/Colombo. Trend = 6 months ending with the chosen month.
- **D18 Spending metrics (T21)** — savings = two metrics: `leftOver` = income − expense, and `savedToCategory` = expenses in the default 'Savings' category (already inside expense, so not counted twice). No refund type: a refund is entered as INCOME in the 'Other' income category (known limit). Payday window (D10) = the income day + the next 2 days, from every INCOME record; overlapping windows count an expense once. Needs/wants from the category's need/want; no category or neither = `unclassified`. Ratios = part ÷ income, 4 decimals HALF_UP, `null` when income is 0. `fixedBills` = active bills (planned amounts).
- **D19 Pattern rules (T22)** — extra thresholds not in D10: payday spending = payday-window spend > 30% of the month's expense; weekend spending = average per weekend day > 1.5 × average per weekday (days counted in the month). All rules strict `>` (exactly at threshold = no insight). `GET /insights?month=` recalculates every time and replaces that month's saved rows.

- **D20 Health score (formerly P05; team-approved 2026-11-21)** — deterministic 0-100 score, not a forecast. Component weights: Spending Control 45, Debt Payments 20, Goal Progress 15, Savings 10, Emergency Savings 10. Weights are score points, not income percentages.
  - Inputs: I = monthly income; E = all monthly expenses; S = expenses categorized as Savings; C = E - S; N = monthly NEED expenses; B = explicitly user-declared, dated emergency reserve. `clamp(x)` means max(0,min(1,x)).
  - Spending Control (45): if I > 0 and C > 0, `27*min(1,I/C) + 18*clamp((I-C)/(0.05*I))`; if C = 0 with I > 0, award 45.
  - Debt Payments (20): `20*sum(loanBillAmount*paidMarker)/sum(loanBillAmount)` for eligible obligations. This measures recorded paid markers only; it does not prove the amount or timeliness of repayment.
  - Goal Progress (15): `15*sum(min(contributionsThroughMonthEnd,targetAmount))/sum(targetAmount)` for eligible goals.
  - Savings (10): `A = min(S,max(0,I-E+S))`; points = `10*clamp(A/(0.05*I))` when I > 0. Savings-category transactions are already expenses and must not be deducted twice.
  - Emergency Savings (10): use `r=B/N`; linearly interpolate points through `(r,points)=(0,0),(0.25,2.5),(0.5,5),(1,7.5),(3,10)`; cap at 10. B must be explicitly declared for the relevant date; never infer it from balances, savings transactions or goals.
  - Missing data: missing required inputs make the component and total unavailable, not zero. Zero income makes income-dependent scoring unavailable; missing B or N <= 0 makes emergency scoring unavailable. Explicitly no applicable debt or goals means NOT_APPLICABLE, not automatic full points. Normalize applicable weights: total = HALF_UP integer rounding of `100*sum(componentPoints)/sum(applicableWeights)`, clamped 0-100; component points use 2 decimal HALF_UP.
  - Implementation: BigDecimal calculations, deterministic results, no predictions. A dated emergency-reserve input and remaining T21/T18/T19 dependencies must be resolved before T23 acceptance. The Rs.100,000 example is illustrative until its full inputs are documented.

- **D21 Backups (T56.4, answers T36.3)** — nightly `mysqldump` of the `finora` schema, gzip, encrypted with `gpg --symmetric --cipher-algo AES256` (passphrase file root-only on the server, second copy kept offline by the lead). Backups are kept **30 days**, then deleted automatically; so data a user deletes (T36) is gone from all backups within 30 days.
- **D22 Debug networking (T37.3)** — debug builds call `http://127.0.0.1:8080/`. Before testing, run `adb reverse tcp:8080 tcp:8080` (USB or ADB-over-Wi-Fi; repeat after reconnecting) so the phone's port 8080 reaches the laptop's API. Works on real phones and the emulator. Debug cleartext is allowed only for `127.0.0.1` (replaces `10.0.2.2`); release builds allow no cleartext.
- **D23 Release API URL** — release builds get the server URL from `FINORA_RELEASE_API_URL` at build time (HTTPS origin, path `/`, checked in `build.gradle`). The real URL is fixed when T56 has a server and domain; T37 and other Android tasks do not wait for it.

## Proposed — still open

- _None currently._
