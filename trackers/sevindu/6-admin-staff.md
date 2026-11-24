# Sevindu 6 — Admin (5 functions) and Staff API (T41–T47)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Roles approved (D01).
**Detail: outline** — expand exact fields/endpoints/tests before coding.

---

### T41 · Admin login and dashboard · `ADMIN-01`
**Lead:** Sevindu · **Depends on:** T06, T08 · **Touches:** `api:admin/`, `and:ui/admin/`
- [x] T41.1 [API] Seed first ADMIN from env vars on startup (no hard-coded password)
- [x] T41.2 [API] `GET /api/v1/admin/stats` → user counts by status, staff count, open tickets, active SMS formats (no financial data)
- [x] T41.3 [AND] Admin home screen with these counts and links to T42–T45

**Done when**
- [x] Tests: USER/STAFF tokens and forged/expired tokens → 401/403; response contains no amounts

**Handoff notes:** D49/D53. MySQL verified first-ADMIN bootstrap, exact five counts and role/expired-token denial. Android 17 verified the new end-to-end USER and default-category links alongside existing STAFF and SMS destinations. Formal dependencies and independent review remain open.

---

### T42 · Admin manages user accounts · `ADMIN-02`
**Lead:** Sevindu · **Depends on:** T41 · **Touches:** `api:admin/`, `and:ui/admin/`
- [x] T42.1 [API] `GET /api/v1/admin/users?q=` (email/name search) → id, email, name, status, created_at only
- [x] T42.2 [API] `PUT /api/v1/admin/users/{id}/status` `{status, reason}` → audit event; disabling revokes tokens
- [x] T42.3 [AND] User list + enable/disable dialog with reason

**Done when**
- [x] Tests: disabled user cannot log in; admin has no endpoint returning transactions/budgets/goals

**Handoff notes:** D53. Isolated MySQL verified the exact USER projection, status audit, old credential invalidation across re-enable and finance denial. Android 17 verified search, disable and enable from ADMIN Home. Formal T41 and independent review remain open.

---

### T43 · Admin manages staff accounts · `ADMIN-03`
**Lead:** Sevindu · **Depends on:** T41 · **Touches:** `api:admin/`, `and:ui/admin/`
- [x] T43.1 [API] `POST /api/v1/admin/staff` create STAFF (temporary password, must change at first login)
- [x] T43.2 [API] Disable/enable staff; role field cannot be set to ADMIN by any endpoint
- [x] T43.3 [AND] Staff list + create/disable

**Done when**
- [x] Tests: staff cannot create staff/admins or change own role; disabled staff session → 403

**Handoff notes:** D49. Isolated Android 17 verified STAFF list, create, first-login activation and disable using the UI. MySQL denied STAFF provisioning and role changes, returned 403 while disabled and rejected the old credentials after re-enable. Formal T41 and independent review remain open.

---

### T44 · Admin default categories · `ADMIN-04 / FR12`
**Lead:** Sevindu · **Depends on:** T41, T09 · **Touches:** `api:admin/`, `api:category/`, `and:ui/admin/`
- [x] T44.1 [API] CRUD defaults (`user_id NULL`) with EN + SI names; deactivate instead of delete when in use
- [x] T44.2 [API] Unique name per kind; user-owned categories never changed
- [x] T44.3 [AND] Category management screen

**Done when**
- [x] Tests: USER cannot edit defaults; deactivated default stays on old transactions

**Handoff notes:** D53. V19 enforces unique names per kind among defaults. MySQL preserved transaction and budget references when deactivating an in-use default and rejected USER edits. Android 17 verified create, edit and removal from ADMIN Home. Formal T41/T09 and independent review remain open.

---

### T45 · Admin bank SMS formats · `ADMIN-05 / FR12`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T41, T14 · **Touches:** `api:smsformat/`, `and:ui/admin/`
- [x] T45.1 [API] CRUD versioned formats per bank (pattern + field mapping); only one active version per bank
- [x] T45.2 [API] Validate a format against redacted sample text before activating
- [x] T45.3 [API] `GET /api/v1/sms-formats` (USER) → active formats for the app's local parser (T14)
- [x] T45.4 [API] Roll back = activate an older version
- [x] T45.5 [AND] Format list, edit, test-sample, activate/rollback

**Done when**
- [x] Tests: invalid pattern rejected; rollback restores previous; T16 regression set still passes after a change

**Handoff notes:**
- D35/D36: NSB is the approved bank. USER receives the unchanged schema-1 snapshot or an authoritative empty response when disabled. New production definitions require separate approval.
- D54/V20 adds ADMIN-only persisted drafts and server-side validation of all three synthetic fixtures using the Android parser. Editing clears evidence. Publication and activation require the approved definition plus matching candidate and fixture digests. Published definitions are immutable.
- MySQL verified authorization, invalid definitions, stale and modified revisions, simultaneous activation, rollback and unchanged USER results. Android 17 verified ADMIN draft edit/save, fixture validation, publish, activation and rollback. D35 parser regressions remain green.
- Formal T41/T14 prerequisites and independent review remain open.

---

### T46 · Staff login and dashboard · `STAFF-01`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T06, T08, T43 · **Touches:** `api:staff/`, `and:ui/staff/`
- [x] T46.1 [API] `GET /api/v1/staff/stats` → my open tickets, unassigned tickets
- [x] T46.2 [AND·Sahan] Staff home screen

**Done when**
- [x] Tests: STAFF → `/admin/**` 403; STAFF → user finance endpoints 403; disabled staff → 403

**Handoff notes:** D45 early start. Retained MySQL EndpointAuthTest and StaffStatsControllerTest reports verify wrong-role denial on ADMIN and USER finance routes and 403 on STAFF stats with a disabled caller. Android verified isolated STAFF dashboard counts and sign-out. Formal dependencies and independent review remain open.

---

### T47 · Staff support requests · `STAFF-02`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T46 · **Touches:** `api:support/`, `and:ui/support/`, `and:ui/staff/`
- [x] T47.1 [API] USER: `POST /api/v1/support/tickets` (subject, body, optional error_code), list own
- [x] T47.2 [API] STAFF: list unassigned/assigned, claim, reply, change status
- [x] T47.3 [API] Strip/reject text that looks like card or account numbers; no attachments in v1
- [x] T47.4 [AND·Sahan] User "Help" form + staff ticket screens

**Done when**
- [x] Tests: user sees only own tickets; staff can reply only to claimed tickets; account-number text redacted

**Handoff notes:** D49/D50/D52. The HMD Crest verified Help and STAFF ticket screens, claim and bilingual replies. MySQL verified owner/assignee isolation and removal of bounded labeled account/card lines before persistence, while rejecting unsafe content. A synthetic regression found no identifiers in captured application logs or audit details. Formal dependencies and independent review remain open.
