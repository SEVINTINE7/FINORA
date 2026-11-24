# Sahan 5 — Android privacy, staff help, Android tests (T40, T48, T49, T51)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T40 · Android privacy and resilience · `NFR-SEC`
**Lead:** Sahan · **Depends on:** T37, T08 · **Touches:** `and:`, `AndroidManifest.xml`, `res:xml/`
- [ ] T40.1 Tokens only in EncryptedSharedPreferences; no keys in APK; release logs stripped
- [x] T40.2 `allowBackup=false` or backup rules excluding tokens; `FLAG_SECURE` on financial screens
- [ ] T40.3 Handle network loss, process death, rotation and expired session gracefully

**Done when**
- [ ] Manual checklist on device passed (recorded in Handoff notes); `adb logcat` shows no tokens/SMS

**Handoff notes:** D45 early start. Unsigned release APK checked for TLS, backup and transfer exclusions, debug-only READ_SMS, credential signatures and app logger calls. Android 17 verified secure login and language-dialog windows and blocked STAFF screen capture. Device privacy and resilience checklist remains open.

---

### T48 · Staff application assistance · `STAFF-03`
**Lead:** Sahan · **Depends on:** T46, T47 · **Touches:** `api:support/`, `and:ui/staff/`
- [ ] T48.1 Saved reply templates (login, navigation, SMS permission, language) — staff can insert into ticket replies
- [ ] T48.2 Templates never ask for passwords, OTPs or SMS text
- [x] T48.3 In-app FAQ screen for users (EN/SI)

**Done when**
- [ ] Tests: only STAFF can manage templates; user sees FAQ

**Handoff notes:** D45 early start. USER More → Help verified in English and Sinhala on Android 17. Saved replies await agreed template and ticket policies.

---

### T49 · Staff SMS import troubleshooting · `STAFF-04`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T46, T45 · **Touches:** `and:sms/`, `api:support/`
- [ ] T49.1 [AND] On parse failure the app offers "Report problem" sending only bank code, format version, error code
- [ ] T49.2 [API·Sevindu] Staff view of these reports grouped by bank/error; "Flag to admin" action
- [ ] T49.3 Staff cannot edit SMS formats (admin only, T45)

**Done when**
- [ ] Tests: report payload contains no SMS text, amounts or account numbers; staff edit format → 403

**Handoff notes:** D45 records open decisions for unavailable bank/format metadata, ADMIN escalation and format mutation. No report collection is active.

---

### T51 · Android functional tests · `ALL`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T38 · **Touches:** `android/app/src/androidTest/`
- [x] T51.1 Espresso: register, login, add/edit/delete record, budget, dashboard, SMS permission denied path
- [ ] T51.2 Run on Android 8 (emulator) and a recent real device
- [ ] T51.3 Save real test reports/screenshots

**Done when**
- [ ] `./gradlew connectedV1DebugAndroidTest` output attached

**Handoff notes:** D46 early start. Android 17 isolated emulator passed both Espresso tests. Android 8, recent-handset invocation and cross-device evidence remain open. Keep the original session runner available via the Gradle property.
