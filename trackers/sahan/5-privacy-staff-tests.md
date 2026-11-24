# Sahan 5 — Android privacy, staff help, Android tests (T40, T48, T49, T51)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T40 · Android privacy and resilience · `NFR-SEC`
**Lead:** Sahan · **Depends on:** T37, T08 · **Touches:** `and:`, `AndroidManifest.xml`, `res:xml/`
- [ ] T40.1 Tokens only in EncryptedSharedPreferences; no keys in APK; release logs stripped
- [x] T40.2 `allowBackup=false` or backup rules excluding tokens; `FLAG_SECURE` on financial screens
- [x] T40.3 Handle network loss, process death, rotation and expired session gracefully

**Done when**
- [x] Manual checklist on device passed (recorded in Handoff notes); `adb logcat` shows no tokens/SMS

**Handoff notes:** D45/D50/D51. The fixed HMD debug build restored USER Help and Support (EN/SI) and STAFF Support focus and protected Recents. Expiry, replacement-account isolation and offline evidence remain valid. On 2026-11-25 Sahan confirmed all staged USER FAQ/Support, STAFF reply, Back, retry and expired-session labels were audible and correct in EN/SI. Isolated acceptance-app Logcat had zero credential or SMS pattern matches across the retained consent and listening checks. This closes the physical checklist. T40.1 remains open for D51/T56 authenticated release HTTPS.

---

### T48 · Staff application assistance · `STAFF-03`
**Lead:** Sahan · **Depends on:** T46, T47 · **Touches:** `api:support/`, `and:ui/staff/`
- [x] T48.1 Saved reply templates (login, navigation, SMS permission, language) — staff can insert into ticket replies
- [x] T48.2 Templates never ask for passwords, OTPs or SMS text
- [x] T48.3 In-app FAQ screen for users (EN/SI)

**Done when**
- [x] Tests: only STAFF can manage templates; user sees FAQ

**Handoff notes:** D49/D50. HMD Crest passed the bilingual saved-reply lifecycle and exposed localized FAQ headings, support status and STAFF preview/confirmation nodes at 130% text. MySQL rejects USER/ADMIN catalog management and arbitrary STAFF text. Native-speaker review and formal dependencies remain open. See the checkpoint for accessibility evidence limits.

---

### T49 · Staff SMS import troubleshooting · `STAFF-04`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T46, T45 · **Touches:** `and:sms/`, `api:support/`
- [x] T49.1 [AND] On parse failure the app offers "Report problem" sending only bank code, format version, error code
- [x] T49.2 [API·Sevindu] Staff view of these reports grouped by bank/error; "Flag to admin" action
- [x] T49.3 Staff cannot edit SMS formats (admin only, T45)

**Done when**
- [x] Tests: report payload contains no SMS text, amounts or account numbers; staff edit format → 403

**Handoff notes:** D47/D49. Android 17 verified cancel and confirm with an exact three-field capture and matching persisted metadata. Both reporting paths send null format version; only allowlisted inbox senders can supply a bank code. D53 adds local ADMIN sample checks without changing the diagnostic payload. Formal T45 and independent review remain open.

---

### T51 · Android functional tests · `ALL`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T38 · **Touches:** `android/app/src/androidTest/`
- [x] T51.1 Espresso: register, login, add/edit/delete record, budget, dashboard, SMS permission denied path
- [x] T51.2 Run on Android 8 (emulator) and a recent real device
- [x] T51.3 Save real test reports/screenshots

**Done when**
- [x] `./gradlew connectedV1DebugAndroidTest` output attached

**Handoff notes:** D46/D49/D50. Four Gradle connected tests passed on isolated Android 8 (API 26), and four direct tests passed on HMD Crest (Android 14) in 60.513 s. Reports and the protected-screen capture result are recorded in the MD05 checkpoint. Keep in progress pending formal T38 dependency closure and independent review.
