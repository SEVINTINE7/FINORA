# Sevindu 5 — Alerts, data download and deletion (T34, T35, T36)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand exact fields/endpoints/tests before coding.

---

### T34 · Budget, bill and goal alerts · `FR13 (Should)`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T17, T18, T19, T37 · **Touches:** `api:notification/`, `and:notifications/`
- [ ] T34.1 [API] `POST/DELETE /api/v1/devices` register/remove FCM token
- [ ] T34.2 [API] Daily job creates events: budget NEAR/OVER, bill due in 2 days, goal date near; one alert per event (no duplicates)
- [ ] T34.3 [API] Send via FCM (Firebase Admin SDK, key from env); lock-screen text has no amounts
- [ ] T34.4 [API] User opt-out per alert type in `/me`
- [ ] T34.5 [AND·Sahan] FCM setup, notification channel, Android 13+ permission request, opt-out toggles

**Done when**
- [ ] Tests: job run twice sends once; opted-out user gets nothing; permission refused → app still works

**Handoff notes:** —

---

### T35 · Data download · `FR11`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10, T17, T18, T19 · **Touches:** `api:export/`, `and:ui/settings/`
- [ ] T35.1 [API] `GET /api/v1/me/export` → JSON (or ZIP of CSVs) of all the user's own data; documented in `docs/api-contract.md`
- [ ] T35.2 [API] Stream in memory, no temp files left; safe filename `finora-export-YYYY-MM-DD`
- [ ] T35.3 [AND·Sahan] Download button → save via Storage Access Framework

**Done when**
- [ ] Tests: export contains every owned row and nothing of another user; unauthenticated → 401; no data in logs

**Handoff notes:** —

---

### T36 · Data deletion · `FR11`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T35 · **Touches:** `api:user/`, `and:ui/settings/`
- [ ] T36.1 [API] `DELETE /api/v1/me` with password confirmation → cascade delete all rows, revoke tokens, remove device tokens
- [ ] T36.2 [API] Audit event `USER_DELETED` with no personal or financial payload
- [ ] T36.3 Decide and record backup retention (e.g. backups expire in 30 days) in `docs/decisions.md`
- [ ] T36.4 [AND·Sahan] Two-step confirm screen; clear local storage after success

**Done when**
- [ ] Tests: after delete no rows remain for that user; old tokens → 401; same email can register again

**Handoff notes:** —
