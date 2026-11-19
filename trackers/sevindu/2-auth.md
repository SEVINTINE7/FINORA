# Sevindu 2 — Authentication, roles, consent (T05–T07)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T05 · Secure authentication · `FR01`
**Lead:** Sevindu · **Depends on:** T03, T04 · **Touches:** `api:auth/`, `api:user/`, `api:security/`
- [x] T05.1 `POST /api/v1/auth/register` `{email, password, fullName, language, over18, consentVersion}` → 201 `{userId}`. BCrypt per D09; role USER. `over18=false` → 400 `UNDER_18`; duplicate email → 409 `EMAIL_TAKEN`. Creates consent row
- [x] T05.2 `POST /api/v1/auth/login` `{email, password}` → 200 `{accessToken, refreshToken, expiresIn, role, language}`. Wrong password **and** unknown email → identical 401 `INVALID_CREDENTIALS`. DISABLED → 403 `ACCOUNT_DISABLED`
- [x] T05.3 JWT HS256 with `JWT_SECRET`; claims `sub`=userId, `role`; lifetimes per D07; `JwtAuthFilter` in security chain
- [x] T05.4 `POST /auth/refresh` `{refreshToken}` → new pair, old one revoked (store SHA-256 only). `POST /auth/logout` revokes the refresh token
- [x] T05.5 Add endpoints to `docs/api-contract.md`

**Done when** (integration tests)
- [x] register ok / duplicate / under-18 / weak password
- [x] login ok / wrong password and unknown email give same body / disabled → 403
- [x] expired access token → 401; reusing a rotated refresh token → 401
- [x] password or hash never appears in any response or log line

**Handoff notes:**
- `api:user/`: `User`, `Consent` entities (+ enums `Role`, `UserStatus`, `Language`, repositories); `user_id` is a plain `Long`, no JPA relations. `api:auth/`: `AuthController`, `AuthService`, `RefreshToken`, `dto/`. `api:security/`: `JwtService` (jjwt 0.13.0), `JwtAuthFilter`.
- Logged-in principal = user id: use `@AuthenticationPrincipal Long userId` in controllers. Authority = `ROLE_<role>` (ready for T06 `hasRole`).
- Emails stored trimmed + lower-case. Register also rejects `consentVersion` ≠ `"1.0"` (400 `VALIDATION_ERROR`). Logout is public and always 204.
- `PasswordEncoder` (BCrypt 12) bean in `config/SecurityConfig`; Spring's default in-memory user excluded in `ApiApplication`. Dev profile has a dev-only JWT secret fallback.
- Test tip: tests share one transaction, so after a raw SQL update call `entityManager.clear()`. Tests: `AuthControllerTest` (13); suite 37/37.

---

### T06 · Role-based access control · `ROLE-CR`
**Lead:** Sevindu · **Depends on:** T05 · **Touches:** `api:security/`, `api-test:support/`
- [x] T06.1 URL rules, default deny: `/api/v1/auth/**` public · `/api/v1/admin/**` ADMIN · `/api/v1/staff/**` STAFF · all other `/api/v1/**` USER only (Admin/Staff get 403 on user finance endpoints)
- [x] T06.2 `CurrentUser` helper (id, role from JWT). Ownership pattern: `findByIdAndUserId` → not found = 404
- [x] T06.3 Check user status on each request: DISABLED → 403 even with a valid token
- [x] T06.4 Test helpers: `createUser(role)`, `tokenFor(user)`

**Done when** (integration tests)
- [x] No token → 401; USER → `/admin/**` 403; STAFF → `/admin/**` 403; ADMIN → a USER endpoint 403
- [x] User A reading user B's resource → 404 (use `/me` consents or a test-only endpoint)
- [x] Disabled user's still-valid token → 403

**Handoff notes:**
- `config/SecurityConfig`: role rules + `denyAll()` default. New endpoints under `/api/v1/` are USER-only automatically; admin ones go under `/api/v1/admin/`, staff under `/api/v1/staff/`.
- `api:security/CurrentUser`: static `CurrentUser.id()` / `CurrentUser.role()`. Ownership: `repo.findByIdAndUserId(id, CurrentUser.id())` else `ApiException.notFound(...)`. Example: `ConsentRepository.findByIdAndUserId`.
- `JwtAuthFilter` now loads the user each request: DISABLED → 403 `ACCOUNT_DISABLED`; deleted user → treated as logged out (401). Role still comes from the JWT.
- Test helpers in `support/IntegrationTest`: `createUser(Role)`, `tokenFor(user)`. Test-only USER endpoint `GET /api/v1/test/consents/{id}` in `support/TestOwnershipController` (src/test only).
- Tests: `security/RoleAccessTest` (11); suite 48/48. `docs/api-contract.md` updated with the role rules.


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
