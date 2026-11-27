# Sevindu 8 — More admins, USER→STAFF promotion and one sign-in page (T59–T60, T72)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decisions D62, D63, D66.

---

### T59 · Admin creates admin accounts · `ADMIN-06`
**Lead:** Sevindu · **Support:** Thilina (role tests), Sahan (Android) · **Depends on:** T41, T43 · **Touches:** `api:admin/`, `api:staff/`, `and:ui/admin/`
- [x] T59.1 [API] `POST /api/v1/admin/admins` `{email, fullName, password}` (D09 rule) → 201 `{id, email, fullName, enabled}` with `Cache-Control: no-store`; duplicate email → 409 `CONFLICT`; audit `ADMIN_CREATED` (actor, target only)
- [x] T59.2 [API] `GET /api/v1/admin/admins` (id, email, fullName, enabled, createdAt) and `PATCH /api/v1/admin/admins/{id}` `{enabled}`; disabling yourself or the last active ADMIN → 409 `CONFLICT`; disabling revokes that admin's refresh tokens
- [x] T59.3 [AND·Sahan] Admin home → "Admins": list, create form (password + confirm, show/hide), enable/disable with confirm dialog; EN/SI strings
- [x] T59.4 [API·Thilina] Add the new routes to the T52 role matrix (none, USER, STAFF, ADMIN, disabled)

**Done when**
- [ ] Tests: only ADMIN reaches the routes; weak password 400; duplicate 409; self/last-admin disable 409; new admin signs in and lands on Admin home; password absent from responses and logs

**Handoff notes:**
- 2026-11-27 Sevindu: `api:admin/AdminAccountController` + `AdminAccountService` (JDBC, no migration). Create lower-cases the email, stores BCrypt only, audits `ADMIN_CREATED` (target_type `ADMIN`). Enable/disable locks every ADMIN row `FOR UPDATE`, checks the actor is still active, refuses self/last-admin (409), bumps `staff_session_generation` and revokes refresh tokens on disable. Contract added to `docs/api-contract.md`.
- Tests: `AdminAccountControllerTest` 6/6; full `mvnw verify` 519 run, 0 failures/errors, 1 skipped, JaCoCo met.
- T59.4: [hosted API job](https://github.com/SEVINTINE7/FINORA/actions/runs/45969996509/job/140407825126), PR #34 at `9c6ad9bd1142dbc7805a0bb33336f48e99b3ae41` on upstream `5948f1dbe75bff114801465ad4f14234c1dabf8f`: all three ADMIN mappings discovered, `EndpointAuthTest` 7/7 and `AdminAccountControllerTest` 6/6 passed. Inventory 111 total/101 protected/10 auth; 527 tests run, 0 failures/errors, 1 skip. Existing matrix and feature tests cover this criterion; original implementation/test authorship retained.
- PR #39 adds the Android Admins list and create/enable-disable UI with EN/SI resources. The isolated connected test created an ADMIN, signed in as that ADMIN to Admin home, then disabled it from the original ADMIN. Independent and physical release acceptance remain.

---

### T60 · Promote a USER to STAFF · `ADMIN-07`
**Lead:** Sevindu · **Support:** Thilina (privacy tests), Sahan (Android) · **Depends on:** T36, T42, T43 · **Touches:** `api:admin/`, `api:user/`, `and:ui/admin/`
- [x] T60.1 [API] `POST /api/v1/admin/users/{id}/promote-to-staff` `{confirm: "DELETE_FINANCIAL_DATA"}` → 200 staff summary (no amounts). Missing/wrong confirm → 400; target not an ACTIVE USER, or yourself → 409; unknown id → 404
- [x] T60.2 [API] One transaction under the owner lock: reuse the T36 deletion path for financial rows only (extract a shared method), keep email/name/password, set role STAFF, revoke refresh tokens and device tokens; audit `USER_PROMOTED_TO_STAFF` (actor, target only)
- [x] T60.3 [AND·Sahan] Admin user list → "Make staff": warning dialog that says the user's financial data is erased and cannot be undone, typed confirmation, then refresh lists; EN/SI
- [x] T60.4 [API·Thilina] Privacy tests: the promoted account sees no finance endpoints (403) and no data remains; other users untouched

**Done when**
- [ ] Tests: all T60.1 error cases; row counts for the promoted user are zero in every financial table and unchanged for another user; old access/refresh tokens rejected; promoted user signs in as STAFF and gets the Staff home

**Handoff notes:**

- PR #39 implements D63 and shares financial erasure with T36. Role-aware owner locking rejects late financial writes. The disposable MySQL regression verifies financial row counts, preserved login identity, revoked credentials, ADMIN role and confirmation rules and the privacy allowlist. The isolated emulator confirmed typed promotion and STAFF sign-in. Parent acceptance remains open for independent review and the T36/T42 dependency reviews.

---

### T72 · One sign-in page for every role · `AUTH-04`
**Lead:** Sevindu · **Support:** Sahan (Android), Thilina (role tests) · **Depends on:** T46, T59 · **Touches:** `api:staff/`, `mig:`, `and:ui/login/`, `and:ui/staff/StaffAccessActivity`, `and:ui/admin/`, `and:ui/auth/SessionNavigation`
**Detail: outline** — expand the exact endpoints, the migration for pending staff and the tests, and show them to Sevindu before coding.
- [ ] T72.1 [API] An ADMIN creates STAFF the way T59.1 creates an ADMIN: `{email, fullName, password}` (D09 rule), and the account is ACTIVE at once. Retire the temporary password, the activation route and `POST /api/v1/auth/staff/activate`, and decide what happens to staff still waiting for activation
- [ ] T72.2 [AND] Remove "Sign-in options → Set up staff access" from the Login screen; staff, admins and users sign in on the same form. Admin staff screen: create form with password + confirm, like the Admins form
- [ ] T72.3 [AND] After sign-in, `SessionNavigation` opens the home for the role: USER → Home tab, STAFF → Staff home, ADMIN → Admin home. A disabled account shows the same sign-in error for every role
- [ ] T72.4 [API·Thilina] Role-matrix and journey tests for the new and retired routes

**Done when**
- [ ] Tests: weak password 400; duplicate 409; retired routes gone; a new STAFF signs in on the Login screen and lands on Staff home; ADMIN and USER land on their homes; disabled STAFF cannot sign in; password absent from responses and logs

**Handoff notes:**
