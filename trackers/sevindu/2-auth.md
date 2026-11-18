# Sevindu 2 — Authentication, roles, consent (T05–T07)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T05 · Secure authentication · `FR01`
**Lead:** Sevindu · **Depends on:** T03, T04 · **Touches:** `api:auth/`, `api:user/`, `api:security/`
- [ ] T05.1 `POST /api/v1/auth/register` `{email, password, fullName, language, over18, consentVersion}` → 201 `{userId}`. BCrypt per D09; role USER. `over18=false` → 400 `UNDER_18`; duplicate email → 409 `EMAIL_TAKEN`. Creates consent row
- [ ] T05.2 `POST /api/v1/auth/login` `{email, password}` → 200 `{accessToken, refreshToken, expiresIn, role, language}`. Wrong password **and** unknown email → identical 401 `INVALID_CREDENTIALS`. DISABLED → 403 `ACCOUNT_DISABLED`
- [ ] T05.3 JWT HS256 with `JWT_SECRET`; claims `sub`=userId, `role`; lifetimes per D07; `JwtAuthFilter` in security chain
- [ ] T05.4 `POST /auth/refresh` `{refreshToken}` → new pair, old one revoked (store SHA-256 only). `POST /auth/logout` revokes the refresh token
- [ ] T05.5 Add endpoints to `docs/api-contract.md`

**Done when** (integration tests)
- [ ] register ok / duplicate / under-18 / weak password
- [ ] login ok / wrong password and unknown email give same body / disabled → 403
- [ ] expired access token → 401; reusing a rotated refresh token → 401
- [ ] password or hash never appears in any response or log line

**Handoff notes:** —

---

### T06 · Role-based access control · `ROLE-CR`
**Lead:** Sevindu · **Depends on:** T05 · **Touches:** `api:security/`, `api-test:support/`
- [ ] T06.1 URL rules, default deny: `/api/v1/auth/**` public · `/api/v1/admin/**` ADMIN · `/api/v1/staff/**` STAFF · all other `/api/v1/**` USER only (Admin/Staff get 403 on user finance endpoints)
- [ ] T06.2 `CurrentUser` helper (id, role from JWT). Ownership pattern: `findByIdAndUserId` → not found = 404
- [ ] T06.3 Check user status on each request: DISABLED → 403 even with a valid token
- [ ] T06.4 Test helpers: `createUser(role)`, `tokenFor(user)`

**Done when** (integration tests)
- [ ] No token → 401; USER → `/admin/**` 403; STAFF → `/admin/**` 403; ADMIN → a USER endpoint 403
- [ ] User A reading user B's resource → 404 (use `/me` consents or a test-only endpoint)
- [ ] Disabled user's still-valid token → 403

**Handoff notes:** —

---

### T07 · Consent and account settings · `FR01 / FR11`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T05, T37 · **Touches:** `api:user/`, `and:ui/auth/`, `and:ui/settings/`, `res:values*/strings.xml`
- [ ] T07.1 [API] Consent row on register (version D12, over18, accepted_at)
- [ ] T07.2 [API] `GET /api/v1/me` → `{email, fullName, language, consentVersion}`; `PUT /api/v1/me` `{fullName, language}`
- [ ] T07.3 [API] `POST /api/v1/me/consent/withdraw` → sets `withdrawn_at`, revokes all refresh tokens; next login asks to re-consent or delete data (deletion = T36)
- [ ] T07.4 [AND·Sahan] Register screen: email, name, password, language, over-18 checkbox, consent checkbox + consent text (EN/SI). Submit disabled until both ticked
- [ ] T07.5 [AND·Sahan] Settings screen: language picker (applies at once, saved via `PUT /me`), withdraw consent, "Delete my data" entry (opens T36 flow)

**Done when**
- [ ] API tests for `/me` GET/PUT and withdraw (tokens revoked afterwards)
- [ ] On a device: cannot register without both ticks; language change switches UI to Sinhala

**Handoff notes:** —
