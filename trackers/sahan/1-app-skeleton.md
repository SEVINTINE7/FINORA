# Sahan 1 — App skeleton, role routing, languages (T37, T08, T39)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T37 · Mobile app skeleton · `ALL`
**Lead:** Sahan · **Depends on:** T02 · **Touches:** `/android`, `.github/workflows/ci.yml`
- [x] T37.1 Create `/android` project in Android Studio: Java, package `lk.finora.app`, minSdk 26, target latest stable SDK, Gradle wrapper committed
- [x] T37.2 Single named `v1` flavor, application ID `lk.finora.app`, `ADVICE_MODE = "V1"`. D03 previously required two flavors; formal reconciliation is pending.
- [x] T37.3 Debug API uses `http://127.0.0.1:8080/` with ADB reverse on phone or emulator (D22); map to the actual host API port. Release remains HTTPS-only with the origin deferred to T56 (D23).
- [x] T37.4 Retrofit + OkHttp + Gson; `and:data/api/ApiClient.java` with `AuthInterceptor` (adds `Bearer` token); error JSON → `ApiError` model
- [x] T37.5 Structure: `and:data/{api,repo,local}`, `and:ui/<feature>/`, `and:util/`; ViewModel + LiveData; `TokenStore` on EncryptedSharedPreferences
- [x] T37.6 Reusable loading / empty / error view; `res/values/strings.xml` + `res/values-si/strings.xml` from day one
- [x] T37.7 Login screen (email, password) calling `POST /api/v1/auth/login`
- [ ] T37.8 V1 Android CI runs `./gradlew assembleV1Debug testV1DebugUnitTest`; verify the updated remote workflow.

**Done when**
- [ ] V1 Android CI job green on the consolidated revision
- [ ] V1 installs on an authorized Android 8+ phone, signs in to the dev API via ADB reverse, and reopens encrypted tokens after app restart.
- [ ] Original D03 side-by-side phone criterion remains unverified; confirm its replacement with the V1-only criterion.

**Handoff notes:**
- V1 only (lk.finora.app); retained professional sign-in, adaptive icon and T56 environment-driven signing. Formal D03 reconciliation pending.
- V1 APK and restart-test APK built; 19 unit tests passed, zero lint issues. Original two-flavor CI evidence is run 44900175390.
- D22 loopback uses ADB reverse on phone and emulator; emulator mapped device 8080 to dev API 18080 and verified real login.
- After emulator force-stop, instrumentation reopened both encrypted tokens and the recovered access token passed /me.
- D23 defers the release origin to T56; HTTPS validation and release cleartext denial are retained.
- Pending: consolidated remote CI, physical Android 8+ install/login/persistence, acceptance baseline confirmation and independent review.

---

### T08 · Three-role login routing · `ROLE-CR`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T06, T37 · **Touches:** `and:ui/auth/`, `and:ui/main/`, `and:ui/admin/`, `and:ui/staff/`, `and:data/api/`
- [ ] T08.1 After login route by `role`: USER → `MainActivity` (bottom nav), ADMIN → `AdminHomeActivity`, STAFF → `StaffHomeActivity` (placeholders until T41/T46)
- [ ] T08.2 OkHttp `Authenticator`: on 401 call `/auth/refresh` once; if it fails clear `TokenStore` → login screen
- [ ] T08.3 Remember session on app restart; logout calls `/auth/logout` and clears storage
- [ ] T08.4 Show messages for `ACCOUNT_DISABLED`, `INVALID_CREDENTIALS` (EN/SI)
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
