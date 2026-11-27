# Thilina 6 — Forgot password (T61)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decision D64.

---

### T61 · Forgot password by emailed code · `AUTH-05`
**Lead:** Thilina · **Support:** Sevindu (server mail setup), Sahan (Android) · **Depends on:** T05 · **Touches:** `api:auth/`, `mig:`, `api/pom.xml`, `deploy/finora.env.example`, `docs/deploy.md`, `and:ui/login/`
- [x] T61.1 [API] `spring-boot-starter-mail`; SMTP from env `FINORA_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` only; plain-text EN/SI email by the account language containing only the code and its expiry. Tests use a fake mail sender, never real SMTP
- [x] T61.2 [API] Flyway `password_reset_codes` (`user_id` FK cascade, `code_hash`, `expires_at DATETIME(6)`, `attempts`, `created_at DATETIME(6)`); `POST /api/v1/auth/password/forgot` `{email}` → always 202 with the same body; for an active account, replace any old code; max 3 per account per hour
- [x] T61.3 [API] `POST /api/v1/auth/password/reset` `{email, code, newPassword}` (D09 rule) → 204; wrong/expired/used → 400 `INVALID_RESET_CODE` (same message for all); 5 wrong tries delete the code; success sets the password, deletes the code, revokes all refresh tokens, audit `PASSWORD_RESET` (no code, no email)
- [ ] T61.4 [AND·Sahan] Login → "Forgot password?" → email screen → code + new password screen → back to Login with a success message; EN/SI; codes never logged
- [ ] T61.5 [Server·Sevindu] Choose SMTP provider, add `FINORA_MAIL_*` to `deploy/finora.env.example` and the server env, document in `docs/deploy.md`; send one real reset email from the live server

**Done when**
- [ ] Tests: unknown email gets the same 202; expiry, single use, 5-try lock and hourly limit enforced; old refresh tokens rejected after reset; codes/passwords absent from logs; one real email received from the live server

**Handoff notes:**
- 2026-11-27 Thilina: T61.1-3 implemented; 530 tests, 0 failures/errors, 1 existing skip; JaCoCo passed. Android and live SMTP pending.
