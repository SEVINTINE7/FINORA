# Sevindu 8 — More admins and USER→STAFF promotion (T59–T60)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decisions D62, D63.

---

### T59 · Admin creates admin accounts · `ADMIN-06`
**Lead:** Sevindu · **Support:** Thilina (role tests), Sahan (Android) · **Depends on:** T41, T43 · **Touches:** `api:admin/`, `api:staff/`, `and:ui/admin/`
- [x] T59.1 [API] `POST /api/v1/admin/admins` `{email, fullName, password}` (D09 rule) → 201 `{id, email, fullName, enabled}` with `Cache-Control: no-store`; duplicate email → 409 `CONFLICT`; audit `ADMIN_CREATED` (actor, target only)
- [x] T59.2 [API] `GET /api/v1/admin/admins` (id, email, fullName, enabled, createdAt) and `PATCH /api/v1/admin/admins/{id}` `{enabled}`; disabling yourself or the last active ADMIN → 409 `CONFLICT`; disabling revokes that admin's refresh tokens
- [ ] T59.3 [AND·Sahan] Admin home → "Admins": list, create form (password + confirm, show/hide), enable/disable with confirm dialog; EN/SI strings
- [x] T59.4 [API·Thilina] Add the new routes to the T52 role matrix (none, USER, STAFF, ADMIN, disabled)

**Done when**
- [ ] Tests: only ADMIN reaches the routes; weak password 400; duplicate 409; self/last-admin disable 409; new admin signs in and lands on Admin home; password absent from responses and logs

**Handoff notes:**
- 2026-11-27 Sevindu: `api:admin/AdminAccountController` + `AdminAccountService` (JDBC, no migration). Create lower-cases the email, stores BCrypt only, audits `ADMIN_CREATED` (target_type `ADMIN`). Enable/disable locks every ADMIN row `FOR UPDATE`, checks the actor is still active, refuses self/last-admin (409), bumps `staff_session_generation` and revokes refresh tokens on disable. Contract added to `docs/api-contract.md`.
- Tests: `AdminAccountControllerTest` 6/6; full `mvnw verify` 519 run, 0 failures/errors, 1 skipped, JaCoCo met.
- Open: T59.3 Android Admins screen (Sahan), T59.4 matrix routes (Thilina), live check that a created admin lands on Admin home, then Done when.

---

### T60 · Promote a USER to STAFF · `ADMIN-07`
**Lead:** Sevindu · **Support:** Thilina (privacy tests), Sahan (Android) · **Depends on:** T36, T42, T43 · **Touches:** `api:admin/`, `api:user/`, `and:ui/admin/`
- [ ] T60.1 [API] `POST /api/v1/admin/users/{id}/promote-to-staff` `{confirm: "DELETE_FINANCIAL_DATA"}` → 200 staff summary (no amounts). Missing/wrong confirm → 400; target not an ACTIVE USER, or yourself → 409; unknown id → 404
- [ ] T60.2 [API] One transaction under the owner lock: reuse the T36 deletion path for financial rows only (extract a shared method), keep email/name/password, set role STAFF, revoke refresh tokens and device tokens; audit `USER_PROMOTED_TO_STAFF` (actor, target only)
- [ ] T60.3 [AND·Sahan] Admin user list → "Make staff": warning dialog that says the user's financial data is erased and cannot be undone, typed confirmation, then refresh lists; EN/SI
- [ ] T60.4 [API·Thilina] Privacy tests: the promoted account sees no finance endpoints (403) and no data remains; other users untouched

**Done when**
- [ ] Tests: all T60.1 error cases; row counts for the promoted user are zero in every financial table and unchanged for another user; old access/refresh tokens rejected; promoted user signs in as STAFF and gets the Staff home

**Handoff notes:**
