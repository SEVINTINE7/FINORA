# Sahan 1 — App skeleton, role routing, languages (T37, T08, T39)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T37 · Mobile app skeleton · `ALL`
**Lead:** Sahan · **Depends on:** T02 · **Touches:** `/android`, `.github/workflows/ci.yml`
- [ ] T37.1 Create `/android` project in Android Studio: Java, package `lk.finora.app`, minSdk 26, target latest stable SDK, Gradle wrapper committed
- [ ] T37.2 Product flavors `beta` (applicationIdSuffix `.beta`) and `v1`; `buildConfigField ADVICE_MODE` = `"BETA"` / `"V1"` (D03)
- [ ] T37.3 `buildConfigField API_BASE_URL`: debug = `http://10.0.2.2:8080/` (emulator), release = server HTTPS URL. `network_security_config.xml` allows cleartext only for `10.0.2.2` in debug
- [ ] T37.4 Retrofit + OkHttp + Gson; `and:data/api/ApiClient.java` with `AuthInterceptor` (adds `Bearer` token); error JSON → `ApiError` model
- [ ] T37.5 Structure: `and:data/{api,repo,local}`, `and:ui/<feature>/`, `and:util/`; ViewModel + LiveData; `TokenStore` on EncryptedSharedPreferences
- [ ] T37.6 Reusable loading / empty / error view; `res/values/strings.xml` + `res/values-si/strings.xml` from day one
- [ ] T37.7 Login screen (email, password) calling `POST /api/v1/auth/login`
- [ ] T37.8 Add Android job to CI: `./gradlew assembleV1Debug assembleBetaDebug testV1DebugUnitTest`

**Done when**
- [ ] CI Android job green
- [ ] App installs on a real Android 8+ phone, both flavors side by side, login works against the local API

**Handoff notes:** —

---

### T08 · Three-role login routing · `ROLE-CR`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T06, T37 · **Touches:** `and:ui/auth/`, `and:ui/main/`, `and:ui/admin/`, `and:ui/staff/`, `and:data/api/`
- [ ] T08.1 After login route by `role`: USER → `MainActivity` (bottom nav), ADMIN → `AdminHomeActivity`, STAFF → `StaffHomeActivity` (placeholders until T41/T46)
- [ ] T08.2 OkHttp `Authenticator`: on 401 call `/auth/refresh` once; if it fails clear `TokenStore` → login screen
- [ ] T08.3 Remember session on app restart; logout calls `/auth/logout` and clears storage
- [ ] T08.4 Show messages for `ACCOUNT_DISABLED`, `TOO_MANY_ATTEMPTS`, `INVALID_CREDENTIALS` (EN/SI)
- [ ] T08.5 [API·Sevindu] Confirm login response has `role` and `language` (from T05)

**Done when**
- [ ] Each role lands on its own home on a device
- [ ] Expired refresh → back to login; disabled account shows message; USER cannot open admin screens by intent

**Handoff notes:** —

---

### T39 · Sinhala and English support · `FR01`
**Lead:** Sahan · **Depends on:** T37 · **Touches:** `res:values*/strings.xml`, `and:util/LocaleHelper.java`
- [ ] T39.1 Lint rule / review: no hard-coded user-visible strings in layouts or Java
- [ ] T39.2 In-app language switch (AppCompatDelegate per-app locales), synced with `/me.language`
- [ ] T39.3 Sinhala font renders correctly; long Sinhala labels don't overflow
- [ ] T39.4 Native speaker reviews all Sinhala strings (record who/when in Handoff notes)

**Done when**
- [ ] Every screen checked in both languages on a device; TalkBack reads main buttons

**Handoff notes:** —
