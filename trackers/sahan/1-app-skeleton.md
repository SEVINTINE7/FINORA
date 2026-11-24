# Sahan 1 — App skeleton, role routing, languages (T37, T08, T39)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

---

### T37 · Mobile app skeleton · `ALL`
**Lead:** Sahan · **Depends on:** T02 · **Touches:** `/android`, `.github/workflows/ci.yml`
- [x] T37.1 Create `/android` project in Android Studio: Java, package `lk.finora.app`, minSdk 26, target latest stable SDK, Gradle wrapper committed
- [x] T37.2 Ship one Android application using the `v1` flavor, application ID `lk.finora.app`, and `ADVICE_MODE = "V1"` (D03).
- [x] T37.3 Debug API uses `http://127.0.0.1:8080/` with ADB reverse on phone or emulator (D22); map to the actual host API port. Release remains HTTPS-only with the origin deferred to T56 (D23).
- [x] T37.4 Retrofit + OkHttp + Gson; `and:data/api/ApiClient.java` with `AuthInterceptor` (adds `Bearer` token); error JSON → `ApiError` model
- [x] T37.5 Structure: `and:data/{api,repo,local}`, `and:ui/<feature>/`, `and:util/`; ViewModel + LiveData; `TokenStore` on EncryptedSharedPreferences
- [x] T37.6 Reusable loading / empty / error view; `res/values/strings.xml` + `res/values-si/strings.xml` from day one
- [x] T37.7 Login screen (email, password) calling `POST /api/v1/auth/login`
- [x] T37.8 V1 Android CI runs `./gradlew assembleV1Debug testV1DebugUnitTest`.

**Done when**
- [x] V1 Android CI job passed on `c20087e` ([run 44940716004](https://github.com/SEVINTINE7/FINORA/actions/runs/44940716004)).
- [x] V1 installs on an authorized Android 8+ phone, signs in to the dev API via ADB reverse, and reopens encrypted tokens after app restart.
- [x] The Android build provides only the D03-approved V1 application with ID `lk.finora.app`.

**Handoff notes:**
- D03 at `c20087e` records V1 only (`lk.finora.app`). The V1-only build and acceptance criteria are verified.
- V1 debug and restart APKs built, 19 unit tests passed, and lint found no issues. [CI run 44928880221](https://github.com/SEVINTINE7/FINORA/actions/runs/44928880221) passed Android and API on `9dd5e0c`.
- SDK ADB 37.0.3 detected the Android 17 emulator. D22 reverse mapped device 8080 to dev API 18080. Gradle resolved OkHttp and MockWebServer 4.12.1.
- After emulator force-stop, instrumentation reopened both encrypted tokens and the recovered access token passed /me.
- D23 defers the release origin to T56; HTTPS validation and release cleartext denial are retained.
- Sahan accepted HMD Crest 5G (Android 14) install, login and encrypted restart evidence on 2026-11-22. Sahan relayed Sevindu's independent approval of `51a8d3a` and authorization to close T37. [CI passed on the reviewed revision](https://github.com/SEVINTINE7/FINORA/actions/runs/44941142531).

---

### T08 · Three-role login routing · `ROLE-CR`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T06, T37 · **Touches:** `and:ui/auth/`, `and:ui/main/`, `and:ui/admin/`, `and:ui/staff/`, `and:data/api/`
- [x] T08.1 After login route by `role`: USER → `MainActivity` (bottom nav), ADMIN → `AdminHomeActivity`, STAFF → `StaffHomeActivity` (placeholders until T41/T46)
- [x] T08.2 OkHttp `Authenticator`: on an eligible 401 refresh once and retry the protected call at most once; clear `TokenStore` and return to login on definitive rejection or invalid successful response data. Preserve the session for retry on transient or ambiguous failure per D07.
- [x] T08.3 Remember session on app restart; logout calls `/auth/logout` and clears storage
- [x] T08.4 Show messages for `ACCOUNT_DISABLED`, `INVALID_CREDENTIALS` (EN/SI)
- [x] T08.5 [API·Sevindu] Confirm login response has `role` and `language` (from T05)

**Done when**
- [x] Each role lands on its own home on a device
- [x] Expired refresh → back to login; disabled account shows message; USER cannot open admin screens by intent

**Handoff notes:**
- HMD Crest 5G (Android 14) routed actual USER, ADMIN and STAFF logins. Both external and app-internal launches of privileged screens were denied while USER was signed in.
- Sahan accepted T08.1–T08.4 physical evidence at `3d2b773`, including real refresh after a controlled 401, expired-refresh rejection, restore, online and offline logout, Back protection, and English/Sinhala errors.
- Independent review corrections add USER navigation placeholders, bind protected calls before dispatch, and retain the latest logout revocation credential on storage failure. Both APKs built, 34 unit tests passed, and lint reported zero issues.
- Corrected build `0de509e` passed the targeted HMD recheck for all five USER tabs, role guards, refresh, restoration, online and offline logout, and Back protection.
- Sahan relayed Sevindu's verbal approval of the D07 refresh policy and confirmation of T08.5. Live login responses included role and language.
- Sahan reported an independent source re-review with a conditional GO for `0de509e`. The policy condition is resolved. Exact-revision CI and independent final approval remain pending.

---

### T39 · Sinhala and English support · `FR01`
**Lead:** Sahan · **Depends on:** T37 · **Touches:** `res:values*/strings.xml`, `and:util/LocaleHelper.java`
- [x] T39.1 Lint rule / review: no hard-coded user-visible strings in layouts or Java
- [x] T39.2 In-app language switch (AppCompatDelegate per-app locales), synced with `/me.language`
- [x] T39.3 Sinhala font renders correctly; long Sinhala labels don't overflow
- [x] T39.4 Native speaker reviews all Sinhala strings (record who/when in Handoff notes)

**Done when**
- [x] Every screen checked in both languages on a device; TalkBack reads main buttons

**Handoff notes:**
- All 32 original EN/SI resource pairs are unchanged, all 37 current pairs match, and the Java/layout review found no user-facing literals. USER locale changes require profile confirmation. Guest, ADMIN and STAFF choices remain local.
- Both V1 APKs built after the stable login identity correction, 39 unit tests passed, and lint reported no issues.
- HMD Crest 5G (Android 14) verified both languages on Login and the USER, ADMIN and STAFF homes. Sinhala samples, including all five USER tabs, showed no clipping at the tested settings.
- Sahan confirmed the spoken TalkBack labels for Sign in, Show password and Language, and approved all 37 Sinhala resource pairs as native-speaker reviewer on 2026-11-22.
- Live USER sync passed both ways. The HMD Crest retained confirmed Sinhala on same-process home recreation and on Login after a cold offline restart.
- Sahan reported a technical GO from independent source re-review of `92f2dd3`, including the stable login identity correction. Independent human final approval remains pending. The separate MD04 [64-pair](../../docs/md04-sinhala-native-speaker-approval.md) and [23-pair](../../docs/md04-history-advice-native-speaker-approval.md) approvals are recorded at their reviewed revisions.
