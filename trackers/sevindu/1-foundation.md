# Sevindu 1 — Foundation (T01–T04)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T01 · Confirm implementation decisions · `BASE`
**Lead:** Sevindu · **Support:** Sahan, Thilina · **Depends on:** — · **Touches:** `docs/decisions.md`
- [x] T01.1 Admin/Staff roles approved by lecturer (D01)
- [x] T01.2 Review P01–P03, P06–P08 with the team; move agreed items to **Decided** (now D07–D13)
- [ ] T01.3 Agree P04 thresholds and P05 weights with Thilina; Thilina adds each score part's formula — P04 done (D10); **P05 open: Thilina sets weights + formulas**
- [x] T01.4 Each member confirms their local setup: JDK 17, MySQL 8, Android Studio, Git — all three confirmed

**Done when**
- [x] No **Proposed** item needed by T02–T08 remains in `docs/decisions.md` (only P05 left, needed by T23)

**Handoff notes:**
- P01, P03, P04, P06–P08 accepted → D07, D09–D13. P02 rate limit removed (D08). Git: direct push to `main` (D13). Test DB: local `finora_test` (D11).
- P05 left open for Thilina (weights + formulas); blocks only T23.
- Sevindu's machine: JDK 17.0.12, Maven 3.9.11, Git 2.50, MySQL Server 8.0 (service running), Android Studio + SDK. `mysql` CLI and `adb` not on PATH (optional). No Docker.
- All three members confirmed local setup (T01.4).

---

### T02 · Repository and build setup · `BASE`
**Lead:** Sevindu · **Depends on:** T01 · **Touches:** `/api`, `/docs`, `.gitignore`, `README.md`, `.github/workflows/ci.yml`
- [x] T02.1 Generate Spring Boot 4.1 project (D14) in `/api` — Maven wrapper, Java 17, group `lk.finora`, artifact `api`, package `lk.finora.api`. Starters: web, validation, security, data-jpa, flyway, mysql driver, actuator, test
- [x] T02.2 Add to `.gitignore`: `target/`, `build/`, `.gradle/`, `local.properties`, `*.iml`, `application-local.yml`, `google-services.json`
- [x] T02.3 Root `README.md`: prerequisites, clone, create MySQL DB `finora`, set env vars, run API, open `/android` in Android Studio
- [x] T02.4 GitHub Actions `ci.yml`: on push/PR → JDK 17 → `cd api && ./mvnw -B test`. (Android job added in T37.6)

**Done when**
- [x] Fresh clone → `cd api && ./mvnw test` passes
- [x] CI run is green on GitHub

**Handoff notes:**
- `api/` = Spring Boot 4.1.2 (D14), Java 17, Maven wrapper 3.10.1. Starters: actuator, data-jpa, flyway (+flyway-mysql), security, validation, webmvc, mysql-connector-j; Boot 4 `*-test` starters. Testcontainers removed (D11).
- Config: `application.properties` (DB_URL/DB_USER/DB_PASS, defaults finora/finora local), `ddl-auto=validate`, empty `db/migration/`. Test profile `application-test.properties` → `finora_test` (TEST_DB_*).
- Each machine runs `api/scripts/create-local-db.sql` once as root (README step 2).
- CI `.github/workflows/ci.yml`: MySQL 8 service + `./mvnw -B test`; first run green (run 44360240545). Android job comes in T37.8.
- Verified: local and fresh-clone `./mvnw test` → 1 test, 0 failures. Default Spring Security password still generated until T05.

---

### T03 · Database design and migrations · `FR01–FR15`
**Lead:** Sevindu · **Depends on:** T02 · **Touches:** `mig:`, `docs/db-schema.md`
All tables: `id BIGINT AUTO_INCREMENT PK`, `created_at DATETIME(6)`; every user-owned table has `user_id FK → users ON DELETE CASCADE`.
- [ ] T03.1 `docs/db-schema.md`: Mermaid ER diagram + one line per table
- [ ] T03.2 `V1__auth.sql`
  - `users(email VARCHAR(254) UNIQUE, password_hash VARCHAR(100), full_name VARCHAR(100), role ENUM('USER','ADMIN','STAFF'), status ENUM('ACTIVE','DISABLED'), language ENUM('EN','SI') DEFAULT 'EN', updated_at)`
  - `consents(user_id, version VARCHAR(10), over18 BOOLEAN, accepted_at, withdrawn_at NULL)`
  - `refresh_tokens(user_id, token_hash CHAR(64) UNIQUE, expires_at, revoked_at NULL)`
- [ ] T03.3 `V2__finance_core.sql`
  - `accounts(user_id, name VARCHAR(60), type ENUM('BANK','CASH','CARD','WALLET'), opening_balance DECIMAL(15,2), archived BOOLEAN)` UNIQUE(user_id,name)
  - `categories(user_id NULL /*NULL = default*/, name VARCHAR(60), name_si VARCHAR(60) NULL, kind ENUM('INCOME','EXPENSE'), need_or_want ENUM('NEED','WANT') NULL, active BOOLEAN)`
  - `transactions(user_id, account_id, category_id, type ENUM('INCOME','EXPENSE'), amount DECIMAL(15,2) CHECK (amount>0), txn_date DATE, merchant VARCHAR(100) NULL, note VARCHAR(255) NULL, source ENUM('MANUAL','SMS','PASTE','CSV'), dedup_hash CHAR(64) NULL, updated_at)` INDEX(user_id,txn_date), UNIQUE(user_id,dedup_hash)
- [ ] T03.4 `V3__planning.sql`
  - `budgets(user_id, category_id, month CHAR(7) /*YYYY-MM*/, limit_amount DECIMAL(15,2))` UNIQUE(user_id,category_id,month)
  - `bills(user_id, name, amount, due_day TINYINT 1–31, category_id, active)` · `bill_payments(bill_id, month CHAR(7), paid_at)` UNIQUE(bill_id,month)
  - `goals(user_id, name, target_amount, target_date, status ENUM('ACTIVE','DONE','CANCELLED'))` · `goal_contributions(goal_id, amount, contrib_date)`
- [ ] T03.5 `V4__insights_advice.sql`
  - `insights(user_id, month, type VARCHAR(40), facts JSON)` · `health_scores(user_id, month, total TINYINT 0–100, parts JSON)` UNIQUE(user_id,month)
  - `advice(user_id, month, source ENUM('RULE','AI'), mode ENUM('BETA','V1'), body JSON, verified BOOLEAN)` · `advice_ratings(advice_id, user_id, rating TINYINT 1–5)` UNIQUE(advice_id,user_id)
- [ ] T03.6 `V5__ops.sql`
  - `device_tokens(user_id, token VARCHAR(255) UNIQUE, platform)` · `sms_formats(bank_code VARCHAR(20), version INT, pattern TEXT, active BOOLEAN)` UNIQUE(bank_code,version)
  - `support_tickets(user_id, staff_id NULL, subject, body, status ENUM('OPEN','IN_PROGRESS','CLOSED'), error_code NULL)` · `ticket_replies(ticket_id, author_id, body)`
  - `audit_events(actor_id, action VARCHAR(40), target_type, target_id, reason VARCHAR(255))` — never financial data
- [ ] T03.7 `V6__seed_categories.sql`: default EN/SI categories (Salary, Allowance, Part-time, Food, Food delivery, Transport, Rent, Utilities, Mobile/Internet, Shopping, Entertainment, Health, Education, Loan payment, Savings, Other) with need/want

**Done when**
- [ ] `./mvnw test` runs Flyway on an empty MySQL 8 DB without errors
- [ ] Tests: duplicate budget (same user/category/month) and duplicate rating fail; deleting a user cascades all their rows

**Handoff notes:** —

---

### T04 · Backend API foundation · `ALL`
**Lead:** Sevindu · **Depends on:** T02 · **Touches:** `api:common/`, `api:config/`, `api/src/main/resources/application*.yml`, `docs/api-contract.md`, `api-test:support/`
- [ ] T04.1 Profiles `dev`, `test`, `prod`. Env vars: `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `GROQ_API_KEY`, `GEMINI_API_KEY`. Optional git-ignored `application-local.yml`
- [ ] T04.2 `api:common/error/`: `ApiException(code, HttpStatus)`, `GlobalExceptionHandler` → error JSON from CLAUDE.md (validation → 400 `VALIDATION_ERROR` with field list)
- [ ] T04.3 CORS disabled (Android-only clients). `/actuator/health` public
- [ ] T04.4 springdoc-openapi enabled in `dev` only at `/v3/api-docs`; start `docs/api-contract.md` (table: method, path, role, request, response)
- [ ] T04.5 `api-test:support/IntegrationTest.java` base class using D11 DB + `MockMvc`

**Done when**
- [ ] Test: invalid DTO → 400 with error JSON; unknown path → 404 error JSON; `/actuator/health` → 200
- [ ] `./mvnw test` passes

**Handoff notes:** —
