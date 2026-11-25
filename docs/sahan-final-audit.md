# FINORA Sahan audit continuation

Baseline audited: `265b92e3946a5c528c374b1dc4c67f7437091fb6` on `sahan/md05-integration-review`. The remote branch and PR #16 head matched. Remote main was `b70c36a03cc805a1f48393fcce94db4263fe72c4`. PR #16 was draft.

This is a continuation checkpoint. The criterion register preserves the original obligations and baseline checkbox state. Pending entries require code, test assertions and applicable runtime or historical evidence to be examined before acceptance can be assigned. The prior responsibility matrix supplies task-level context and historical evidence.

## Confirmed findings

- **D55 downstream financial classification remains open.** `MetricsService.java:177` still checks the English Savings display name. `ScoreService.java:105` and `:111` still check the English Loan payment display name. The historical isolated MySQL reproduction is recorded in the MD05 implementation checkpoint. D55 assigns T21/T23 adoption to Thilina. Adoption must include inactive semantic rows, preserve ownership and dates, and be checked across metrics, score, history and advice. None of PRs #10–#15 changes either consumer relative to main.
- **Cross-team merge simulations.** Fresh independent `git merge-tree --write-tree` checks against the baseline were clean for PRs #10 and #15. PRs #11, #13 and #14 conflict in `trackers/thilina/4-planning-tools.md`. PR #12 conflicts in `trackers/thilina/5-qa.md`. Preserve both owners’ tracker entries when integrating. These checks cover Git compatibility.
- **Localization inventory.** Fresh XML parsing counted 460 elements each in English and Sinhala. This count alone leaves key parity, placeholders, wording and device behavior to be checked.
- **Physical and deployment gates.** Android acceptance guide reviewed. ADB listed no devices. T17.3, T18.4 and T19.3 still require their targeted physical acceptance and parent prerequisites. T40.1 stays deferred to T56. T31.3/T32.2 require approved contracts and prerequisite closure or a recorded early start. T34.5, scoped T55 evaluation and T57 remain open.

## Criterion register

All rows below currently await independent criterion-level verification unless explicitly covered in findings above. Cross-team parent-task rows preserve the original obligations for context. Sahan's assigned slice and its owner are recorded in the responsibility matrix.

| Tracker line | Requirement | Baseline | Original obligation |
| --- | --- | --- | --- |
| [trackers/sahan/1-app-skeleton.md #10](../trackers/sahan/1-app-skeleton.md#L10) | T37.1 | Checked | T37.1 Create `/android` project in Android Studio: Java, package `lk.finora.app`, minSdk 26, target latest stable SDK, Gradle wrapper committed |
| [trackers/sahan/1-app-skeleton.md #11](../trackers/sahan/1-app-skeleton.md#L11) | T37.2 | Checked | T37.2 Ship one Android application using the `v1` flavor, application ID `lk.finora.app`, and `ADVICE_MODE = "V1"` (D03). |
| [trackers/sahan/1-app-skeleton.md #12](../trackers/sahan/1-app-skeleton.md#L12) | T37.3 | Checked | T37.3 Debug API uses `http://127.0.0.1:8080/` with ADB reverse on phone or emulator (D22); map to the actual host API port. Release remains HTTPS-only with the origin deferred to T56 (D23). |
| [trackers/sahan/1-app-skeleton.md #13](../trackers/sahan/1-app-skeleton.md#L13) | T37.4 | Checked | T37.4 Retrofit + OkHttp + Gson; `and:data/api/ApiClient.java` with `AuthInterceptor` (adds `Bearer` token); error JSON → `ApiError` model |
| [trackers/sahan/1-app-skeleton.md #14](../trackers/sahan/1-app-skeleton.md#L14) | T37.5 | Checked | T37.5 Structure: `and:data/{api,repo,local}`, `and:ui/<feature>/`, `and:util/`; ViewModel + LiveData; `TokenStore` on EncryptedSharedPreferences |
| [trackers/sahan/1-app-skeleton.md #15](../trackers/sahan/1-app-skeleton.md#L15) | T37.6 | Checked | T37.6 Reusable loading / empty / error view; `res/values/strings.xml` + `res/values-si/strings.xml` from day one |
| [trackers/sahan/1-app-skeleton.md #16](../trackers/sahan/1-app-skeleton.md#L16) | T37.7 | Checked | T37.7 Login screen (email, password) calling `POST /api/v1/auth/login` |
| [trackers/sahan/1-app-skeleton.md #17](../trackers/sahan/1-app-skeleton.md#L17) | T37.8 | Checked | T37.8 V1 Android CI runs `./gradlew assembleV1Debug testV1DebugUnitTest`. |
| [trackers/sahan/1-app-skeleton.md #20](../trackers/sahan/1-app-skeleton.md#L20) | T37 Done when 1 | Checked | V1 Android CI job passed on `c20087e` ([run 44940716004](https://github.com/SEVINTINE7/FINORA/actions/runs/44940716004)). |
| [trackers/sahan/1-app-skeleton.md #21](../trackers/sahan/1-app-skeleton.md#L21) | T37 Done when 2 | Checked | V1 installs on an authorized Android 8+ phone, signs in to the dev API via ADB reverse, and reopens encrypted tokens after app restart. |
| [trackers/sahan/1-app-skeleton.md #22](../trackers/sahan/1-app-skeleton.md#L22) | T37 Done when 3 | Checked | The Android build provides only the D03-approved V1 application with ID `lk.finora.app`. |
| [trackers/sahan/1-app-skeleton.md #36](../trackers/sahan/1-app-skeleton.md#L36) | T08.1 | Checked | T08.1 After login route by `role`: USER → `MainActivity` (bottom nav), ADMIN → `AdminHomeActivity`, STAFF → `StaffHomeActivity` (placeholders until T41/T46) |
| [trackers/sahan/1-app-skeleton.md #37](../trackers/sahan/1-app-skeleton.md#L37) | T08.2 | Checked | T08.2 OkHttp `Authenticator`: on an eligible 401 refresh once and retry the protected call at most once; clear `TokenStore` and return to login on definitive rejection or invalid successful response data. Preserve the session for retry on transient or ambiguous failure per D07. |
| [trackers/sahan/1-app-skeleton.md #38](../trackers/sahan/1-app-skeleton.md#L38) | T08.3 | Checked | T08.3 Remember session on app restart; logout calls `/auth/logout` and clears storage |
| [trackers/sahan/1-app-skeleton.md #39](../trackers/sahan/1-app-skeleton.md#L39) | T08.4 | Checked | T08.4 Show messages for `ACCOUNT_DISABLED`, `INVALID_CREDENTIALS` (EN/SI) |
| [trackers/sahan/1-app-skeleton.md #40](../trackers/sahan/1-app-skeleton.md#L40) | T08.5 | Checked | T08.5 [API·Sevindu] Confirm login response has `role` and `language` (from T05) |
| [trackers/sahan/1-app-skeleton.md #43](../trackers/sahan/1-app-skeleton.md#L43) | T08 Done when 1 | Checked | Each role lands on its own home on a device |
| [trackers/sahan/1-app-skeleton.md #44](../trackers/sahan/1-app-skeleton.md#L44) | T08 Done when 2 | Checked | Expired refresh → back to login; disabled account shows message; USER cannot open admin screens by intent |
| [trackers/sahan/1-app-skeleton.md #58](../trackers/sahan/1-app-skeleton.md#L58) | T39.1 | Checked | T39.1 Lint rule / review: no hard-coded user-visible strings in layouts or Java |
| [trackers/sahan/1-app-skeleton.md #59](../trackers/sahan/1-app-skeleton.md#L59) | T39.2 | Checked | T39.2 In-app language switch (AppCompatDelegate per-app locales), synced with `/me.language` |
| [trackers/sahan/1-app-skeleton.md #60](../trackers/sahan/1-app-skeleton.md#L60) | T39.3 | Checked | T39.3 Sinhala font renders correctly; long Sinhala labels don't overflow |
| [trackers/sahan/1-app-skeleton.md #61](../trackers/sahan/1-app-skeleton.md#L61) | T39.4 | Checked | T39.4 Native speaker reviews all Sinhala strings (record who/when in Handoff notes) |
| [trackers/sahan/1-app-skeleton.md #64](../trackers/sahan/1-app-skeleton.md#L64) | T39 Done when 1 | Checked | Every screen checked in both languages on a device; TalkBack reads main buttons |
| [trackers/sahan/2-entry-sms.md #15](../trackers/sahan/2-entry-sms.md#L15) | T11.1 | Checked | T11.1 Add/edit form: type, amount (LKR, 2 decimals), date picker, account, category, merchant, note |
| [trackers/sahan/2-entry-sms.md #19](../trackers/sahan/2-entry-sms.md#L19) | T11.2 | Checked | T11.2 Field validation (amount > 0, required fields) with inline errors |
| [trackers/sahan/2-entry-sms.md #22](../trackers/sahan/2-entry-sms.md#L22) | T11.3 | Checked | T11.3 Keep entered values on rotation and on API error; retry button |
| [trackers/sahan/2-entry-sms.md #25](../trackers/sahan/2-entry-sms.md#L25) | T11.4 | Checked | T11.4 Works without SMS permission (always available from the main screen) |
| [trackers/sahan/2-entry-sms.md #29](../trackers/sahan/2-entry-sms.md#L29) | T11 Done when 1 | Checked | Unit tests for validation; on device: rotation and airplane-mode error keep the values |
| [trackers/sahan/2-entry-sms.md #42](../trackers/sahan/2-entry-sms.md#L42) | T13.1 | Checked | T13.1 Note in Handoff: Play Store restricts READ_SMS → trial APK side-loaded (proposal 7.3) |
| [trackers/sahan/2-entry-sms.md #44](../trackers/sahan/2-entry-sms.md#L44) | T13.2 | Checked | T13.2 Explanation screen before asking `READ_SMS`; clear "No thanks" path |
| [trackers/sahan/2-entry-sms.md #46](../trackers/sahan/2-entry-sms.md#L46) | T13.3 | Checked | T13.3 Fallbacks always visible: paste SMS text, CSV import (T12), manual entry (T11) |
| [trackers/sahan/2-entry-sms.md #49](../trackers/sahan/2-entry-sms.md#L49) | T13.4 | Checked | T13.4 Handle "Don't ask again" → link to app settings |
| [trackers/sahan/2-entry-sms.md #53](../trackers/sahan/2-entry-sms.md#L53) | T13 Done when 1 | Checked | On device: grant, deny, deny-forever all lead to a working path |
| [trackers/sahan/2-entry-sms.md #67](../trackers/sahan/2-entry-sms.md#L67) | T14.1 | Checked | T14.1 Read inbox via `Telephony.Sms.Inbox` filtered by known bank sender IDs and date range |
| [trackers/sahan/2-entry-sms.md #69](../trackers/sahan/2-entry-sms.md#L69) | T14.2 | Checked | T14.2 Load and validate bundled and USER-facing remote templates |
| [trackers/sahan/2-entry-sms.md #71](../trackers/sahan/2-entry-sms.md#L71) | T14.3 | Checked | T14.3 Extract amount, date, merchant, debit/credit; unknown format → "needs manual review" |
| [trackers/sahan/2-entry-sms.md #73](../trackers/sahan/2-entry-sms.md#L73) | T14.4 | Checked | T14.4 Full SMS text stays on the phone — never in API calls or logs (rule 1) |
| [trackers/sahan/2-entry-sms.md #78](../trackers/sahan/2-entry-sms.md#L78) | T14 Done when 1 | Checked | JVM unit tests for each supported bank format and an unknown format |
| [trackers/sahan/2-entry-sms.md #79](../trackers/sahan/2-entry-sms.md#L79) | T14 Done when 2 | Checked | Network inspector shows no SMS body sent |
| [trackers/sahan/3-sms-quality.md #11](../trackers/sahan/3-sms-quality.md#L11) | T15.1 | Checked | T15.1 [AND] Preview list of parsed SMS: edit amount/date/merchant/category before saving; select/deselect |
| [trackers/sahan/3-sms-quality.md #13](../trackers/sahan/3-sms-quality.md#L13) | T15.2 | Checked | T15.2 [AND] Compute the D38 reference-less SHA-256 fingerprint before preview edits; report potential duplicates for review |
| [trackers/sahan/3-sms-quality.md #14](../trackers/sahan/3-sms-quality.md#L14) | T15.3 | Checked | T15.3 [API·Sevindu] `POST /api/v1/transactions/batch` (source=SMS/PASTE); duplicate hash → reported, not saved |
| [trackers/sahan/3-sms-quality.md #15](../trackers/sahan/3-sms-quality.md#L15) | T15.4 | Checked | T15.4 [AND] Keep a frozen structured batch in screen/session memory on uncertain outcomes; retry only on explicit confirmation |
| [trackers/sahan/3-sms-quality.md #18](../trackers/sahan/3-sms-quality.md#L18) | T15 Done when 1 | Checked | Tests: same SMS imported twice → saved once; manual entry matching an SMS flagged; retry after network loss |
| [trackers/sahan/3-sms-quality.md #32](../trackers/sahan/3-sms-quality.md#L32) | T16.1 | Checked | T16.1 Use the D38-approved synthetic-only D35 fixture set with documented expected fields |
| [trackers/sahan/3-sms-quality.md #33](../trackers/sahan/3-sms-quality.md#L33) | T16.2 | Checked | T16.2 Cover every supported bank: debit, credit, reversal, ATM, malformed |
| [trackers/sahan/3-sms-quality.md #34](../trackers/sahan/3-sms-quality.md#L34) | T16.3 | Checked | T16.3 Test computes field-level accuracy and prints it; target ≥ 90% |
| [trackers/sahan/3-sms-quality.md #37](../trackers/sahan/3-sms-quality.md#L37) | T16 Done when 1 | Checked | Accuracy report from a real test run saved in Handoff notes |
| [trackers/sahan/4-screens.md #12](../trackers/sahan/4-screens.md#L12) | T20.1 | Checked | T20.1 [API·Sevindu] `GET /api/v1/dashboard?month=` → income, expense, net, by-category totals, 6-month trend |
| [trackers/sahan/4-screens.md #13](../trackers/sahan/4-screens.md#L13) | T20.2 | Checked | T20.2 [AND] Dashboard: totals cards, category pie, trend bar chart (MPAndroidChart), month picker |
| [trackers/sahan/4-screens.md #14](../trackers/sahan/4-screens.md#L14) | T20.3 | Checked | T20.3 [AND] Empty state for new users |
| [trackers/sahan/4-screens.md #17](../trackers/sahan/4-screens.md#L17) | T20 Done when 1 | Checked | API test: dashboard totals equal sum of raw transactions for fixture data |
| [trackers/sahan/4-screens.md #18](../trackers/sahan/4-screens.md#L18) | T20 Done when 2 | Checked | Screen loads < 2 s with one year of data on a mid-range phone |
| [trackers/sahan/4-screens.md #32](../trackers/sahan/4-screens.md#L32) | T38.1 | Checked | T38.1 Bottom nav: Home (dashboard), Records, Plan (budgets/bills/goals), Insights, More (history, settings, help) |
| [trackers/sahan/4-screens.md #33](../trackers/sahan/4-screens.md#L33) | T38.2 | Checked | T38.2 Wire each screen as its API task finishes (track here which are wired) |
| [trackers/sahan/4-screens.md #34](../trackers/sahan/4-screens.md#L34) | T38.3 | Checked | T38.3 Lists refresh after create/update/delete everywhere |
| [trackers/sahan/4-screens.md #37](../trackers/sahan/4-screens.md#L37) | T38 Done when 1 | Checked | Every screen reachable; no stale data after edits (manual checklist in Handoff notes) |
| [trackers/sahan/4-screens.md #52](../trackers/sahan/4-screens.md#L52) | T24.1 | Checked | T24.1 Insights list: each pattern with its numbers (amount, % of income, period) |
| [trackers/sahan/4-screens.md #53](../trackers/sahan/4-screens.md#L53) | T24.2 | Checked | T24.2 Score screen: total 0–100 + five parts, each with its reason |
| [trackers/sahan/4-screens.md #54](../trackers/sahan/4-screens.md#L54) | T24.3 | Checked | T24.3 "General guidance, not professional financial advice" note (EN/SI) |
| [trackers/sahan/4-screens.md #57](../trackers/sahan/4-screens.md#L57) | T24 Done when 1 | Checked | Screen values match Thilina's backend fixture results |
| [trackers/sahan/4-screens.md #70](../trackers/sahan/4-screens.md#L70) | T30.1 | Checked | T30.1 [API·Thilina] `GET /api/v1/advice?month=&mode=V1` — V1 returns AI if verified else RULE (D03/D40) |
| [trackers/sahan/4-screens.md #71](../trackers/sahan/4-screens.md#L71) | T30.2 | Checked | T30.2 [API·Thilina] `POST /api/v1/advice/{id}/rating` 1–5, once per advice per user |
| [trackers/sahan/4-screens.md #72](../trackers/sahan/4-screens.md#L72) | T30.3 | Checked | T30.3 [AND] D03/D40 V1-only advice screen uses the server's verified result or rule fallback |
| [trackers/sahan/4-screens.md #73](../trackers/sahan/4-screens.md#L73) | T30.4 | Checked | T30.4 [AND] `v1` flavor: one advice card with its supporting numbers + rating |
| [trackers/sahan/4-screens.md #74](../trackers/sahan/4-screens.md#L74) | T30.5 | Checked | T30.5 [AND] Mode taken from `BuildConfig.ADVICE_MODE`, never from user input |
| [trackers/sahan/4-screens.md #77](../trackers/sahan/4-screens.md#L77) | T30 Done when 1 | Checked | Tests: duplicate rating → 409; V1 falls back to RULE when check fails |
| [trackers/sahan/4-screens.md #78](../trackers/sahan/4-screens.md#L78) | T30 Done when 2 | Checked | V1 advice and rating checked on a device |
| [trackers/sahan/5-privacy-staff-tests.md #11](../trackers/sahan/5-privacy-staff-tests.md#L11) | T40.1 | Open | T40.1 Tokens only in EncryptedSharedPreferences; no keys in APK; release logs stripped |
| [trackers/sahan/5-privacy-staff-tests.md #12](../trackers/sahan/5-privacy-staff-tests.md#L12) | T40.2 | Checked | T40.2 `allowBackup=false` or backup rules excluding tokens; `FLAG_SECURE` on financial screens |
| [trackers/sahan/5-privacy-staff-tests.md #13](../trackers/sahan/5-privacy-staff-tests.md#L13) | T40.3 | Checked | T40.3 Handle network loss, process death, rotation and expired session gracefully |
| [trackers/sahan/5-privacy-staff-tests.md #16](../trackers/sahan/5-privacy-staff-tests.md#L16) | T40 Done when 1 | Checked | Manual checklist on device passed (recorded in Handoff notes); `adb logcat` shows no tokens/SMS |
| [trackers/sahan/5-privacy-staff-tests.md #24](../trackers/sahan/5-privacy-staff-tests.md#L24) | T48.1 | Checked | T48.1 Saved reply templates (login, navigation, SMS permission, language) — staff can insert into ticket replies |
| [trackers/sahan/5-privacy-staff-tests.md #25](../trackers/sahan/5-privacy-staff-tests.md#L25) | T48.2 | Checked | T48.2 Templates never ask for passwords, OTPs or SMS text |
| [trackers/sahan/5-privacy-staff-tests.md #26](../trackers/sahan/5-privacy-staff-tests.md#L26) | T48.3 | Checked | T48.3 In-app FAQ screen for users (EN/SI) |
| [trackers/sahan/5-privacy-staff-tests.md #29](../trackers/sahan/5-privacy-staff-tests.md#L29) | T48 Done when 1 | Checked | Tests: only STAFF can manage templates; user sees FAQ |
| [trackers/sahan/5-privacy-staff-tests.md #37](../trackers/sahan/5-privacy-staff-tests.md#L37) | T49.1 | Checked | T49.1 [AND] On parse failure the app offers "Report problem" sending only bank code, format version, error code |
| [trackers/sahan/5-privacy-staff-tests.md #38](../trackers/sahan/5-privacy-staff-tests.md#L38) | T49.2 | Checked | T49.2 [API·Sevindu] Staff view of these reports grouped by bank/error; "Flag to admin" action |
| [trackers/sahan/5-privacy-staff-tests.md #39](../trackers/sahan/5-privacy-staff-tests.md#L39) | T49.3 | Checked | T49.3 Staff cannot edit SMS formats (admin only, T45) |
| [trackers/sahan/5-privacy-staff-tests.md #42](../trackers/sahan/5-privacy-staff-tests.md#L42) | T49 Done when 1 | Checked | Tests: report payload contains no SMS text, amounts or account numbers; staff edit format → 403 |
| [trackers/sahan/5-privacy-staff-tests.md #50](../trackers/sahan/5-privacy-staff-tests.md#L50) | T51.1 | Checked | T51.1 Espresso: register, login, add/edit/delete record, budget, dashboard, SMS permission denied path |
| [trackers/sahan/5-privacy-staff-tests.md #51](../trackers/sahan/5-privacy-staff-tests.md#L51) | T51.2 | Checked | T51.2 Run on Android 8 (emulator) and a recent real device |
| [trackers/sahan/5-privacy-staff-tests.md #52](../trackers/sahan/5-privacy-staff-tests.md#L52) | T51.3 | Checked | T51.3 Save real test reports/screenshots |
| [trackers/sahan/5-privacy-staff-tests.md #55](../trackers/sahan/5-privacy-staff-tests.md#L55) | T51 Done when 1 | Checked | `./gradlew connectedV1DebugAndroidTest` output attached |
| [trackers/sevindu/1-foundation.md #8](../trackers/sevindu/1-foundation.md#L8) | T01.1 | Checked | T01.1 Admin/Staff roles approved by lecturer (D01) |
| [trackers/sevindu/1-foundation.md #9](../trackers/sevindu/1-foundation.md#L9) | T01.2 | Checked | T01.2 Review P01–P03, P06–P08 with the team; move agreed items to **Decided** (now D07–D13) |
| [trackers/sevindu/1-foundation.md #10](../trackers/sevindu/1-foundation.md#L10) | T01.3 | Checked | T01.3 Agree P04 thresholds and P05 weights with Thilina; Thilina adds each score part's formula — P04 done (D10); **P05 approved as D20** |
| [trackers/sevindu/1-foundation.md #11](../trackers/sevindu/1-foundation.md#L11) | T01.4 | Checked | T01.4 Each member confirms their local setup: JDK 17, MySQL 8, Android Studio, Git — all three confirmed |
| [trackers/sevindu/1-foundation.md #14](../trackers/sevindu/1-foundation.md#L14) | T01 Done when 1 | Checked | No **Proposed** item needed by T02–T08 remains in `docs/decisions.md` (only P05 left, needed by T23) |
| [trackers/sevindu/2-auth.md #53](../trackers/sevindu/2-auth.md#L53) | T07.1 | Checked | T07.1 [API] Consent row on register (version D12, over18, accepted_at) |
| [trackers/sevindu/2-auth.md #54](../trackers/sevindu/2-auth.md#L54) | T07.2 | Checked | T07.2 [API] `GET /api/v1/me` → `{email, fullName, language, consentVersion}`; `PUT /api/v1/me` `{fullName, language}` |
| [trackers/sevindu/2-auth.md #55](../trackers/sevindu/2-auth.md#L55) | T07.3 | Checked | T07.3 [API] `POST /api/v1/me/consent/withdraw` → sets `withdrawn_at`, revokes normal access and refresh credentials; password sign-in offers restricted re-consent, export or deletion (D15/D54) |
| [trackers/sevindu/2-auth.md #56](../trackers/sevindu/2-auth.md#L56) | T07.4 | Checked | T07.4 [AND·Sahan] Register screen: email, name, password, language, over-18 checkbox, consent checkbox + consent text (EN/SI). Submit disabled until both ticked |
| [trackers/sevindu/2-auth.md #57](../trackers/sevindu/2-auth.md#L57) | T07.5 | Checked | T07.5 [AND·Sahan] Settings screen: language picker (applies at once, saved via `PUT /me`), withdraw consent, "Delete my data" entry (opens T36 flow) |
| [trackers/sevindu/2-auth.md #61](../trackers/sevindu/2-auth.md#L61) | T07 Done when 1 | Checked | API tests for `/me` GET/PUT and withdraw (tokens revoked afterwards) |
| [trackers/sevindu/2-auth.md #62](../trackers/sevindu/2-auth.md#L62) | T07 Done when 2 | Checked | On a device: cannot register without both ticks; language change switches UI to Sinhala |
| [trackers/sevindu/3-records.md #9](../trackers/sevindu/3-records.md#L9) | T09.1 | Checked | T09.1 [API] CRUD `/api/v1/accounts` (owner only; archive instead of delete if it has transactions) |
| [trackers/sevindu/3-records.md #10](../trackers/sevindu/3-records.md#L10) | T09.2 | Checked | T09.2 [API] `/api/v1/categories`: list = defaults + own; CRUD on own only; defaults read-only |
| [trackers/sevindu/3-records.md #11](../trackers/sevindu/3-records.md#L11) | T09.3 | Checked | T09.3 [API] Duplicate name per user → 409; delete category in use → 409 `IN_USE` |
| [trackers/sevindu/3-records.md #12](../trackers/sevindu/3-records.md#L12) | T09.4 | Checked | T09.4 [AND·Sahan] Accounts list + add/edit screen |
| [trackers/sevindu/3-records.md #14](../trackers/sevindu/3-records.md#L14) | T09.5 | Checked | T09.5 [AND·Sahan] Categories list grouped income/expense, add/edit own |
| [trackers/sevindu/3-records.md #18](../trackers/sevindu/3-records.md#L18) | T09 Done when 1 | Checked | Tests: CRUD, duplicate → 409, in-use delete → 409, user B cannot see user A's (404), cannot edit a default category |
| [trackers/sevindu/3-records.md #19](../trackers/sevindu/3-records.md#L19) | T09 Done when 2 | Checked | Screens create/edit/delete against local API |
| [trackers/sevindu/3-records.md #34](../trackers/sevindu/3-records.md#L34) | T10.1 | Checked | T10.1 [API] CRUD `/api/v1/transactions`; account and category must belong to user (or be default) |
| [trackers/sevindu/3-records.md #35](../trackers/sevindu/3-records.md#L35) | T10.2 | Checked | T10.2 [API] List with filters `from`, `to`, `type`, `categoryId`, `accountId`, paging; sorted by `txn_date` desc |
| [trackers/sevindu/3-records.md #36](../trackers/sevindu/3-records.md#L36) | T10.3 | Checked | T10.3 [API] `GET /api/v1/transactions/summary?from&to` → totals income/expense/net (BigDecimal) |
| [trackers/sevindu/3-records.md #37](../trackers/sevindu/3-records.md#L37) | T10.4 | Checked | T10.4 [AND·Sahan] Transaction list with filters + edit/delete |
| [trackers/sevindu/3-records.md #40](../trackers/sevindu/3-records.md#L40) | T10 Done when 1 | Checked | Tests: totals exact to the cent after create/edit/delete; filters; other user's account/category rejected; isolation 404 |
| [trackers/sevindu/3-records.md #55](../trackers/sevindu/3-records.md#L55) | T12.1 | Checked | T12.1 [API] Define template `date,type,amount,category,account,merchant,note` (date `YYYY-MM-DD`) in `docs/csv-template.md` |
| [trackers/sevindu/3-records.md #56](../trackers/sevindu/3-records.md#L56) | T12.2 | Checked | T12.2 [API] `POST /api/v1/imports/csv/preview` → parsed rows + per-row errors + duplicate flags; nothing saved |
| [trackers/sevindu/3-records.md #57](../trackers/sevindu/3-records.md#L57) | T12.3 | Checked | T12.3 [API] `POST /api/v1/imports/csv/confirm` saves selected rows (source=CSV, dedup_hash) |
| [trackers/sevindu/3-records.md #58](../trackers/sevindu/3-records.md#L58) | T12.4 | Checked | T12.4 [API] Limits: ≤1 MB, ≤2000 rows; reject cells starting with `= + - @` (formula injection) |
| [trackers/sevindu/3-records.md #59](../trackers/sevindu/3-records.md#L59) | T12.5 | Checked | T12.5 [AND·Sahan] Pick file → preview list → confirm |
| [trackers/sevindu/3-records.md #62](../trackers/sevindu/3-records.md#L62) | T12 Done when 1 | Checked | Tests: valid file, bad dates/amounts, oversize, formula cells, re-import flags duplicates |
| [trackers/sevindu/4-planning-data.md #9](../trackers/sevindu/4-planning-data.md#L9) | T17.1 | Checked | T17.1 [API] CRUD `/api/v1/budgets` per category per month (`YYYY-MM`) |
| [trackers/sevindu/4-planning-data.md #10](../trackers/sevindu/4-planning-data.md#L10) | T17.2 | Checked | T17.2 [API] `GET /api/v1/budgets?month=` returns limit, used, remaining, state `OK\|NEAR(≥80%)\|OVER` |
| [trackers/sevindu/4-planning-data.md #11](../trackers/sevindu/4-planning-data.md#L11) | T17.3 | Open | T17.3 [AND·Sahan] Budget list with progress bars + add/edit |
| [trackers/sevindu/4-planning-data.md #15](../trackers/sevindu/4-planning-data.md#L15) | T17 Done when 1 | Checked | Tests: used amount counts only that month's expenses in that category; first/last day of month; over-spend state |
| [trackers/sevindu/4-planning-data.md #29](../trackers/sevindu/4-planning-data.md#L29) | T18.1 | Checked | T18.1 [API] CRUD `/api/v1/bills` (amount, due_day, category) |
| [trackers/sevindu/4-planning-data.md #30](../trackers/sevindu/4-planning-data.md#L30) | T18.2 | Checked | T18.2 [API] Mark paid/unpaid per month; due_day 31 in short months = last day |
| [trackers/sevindu/4-planning-data.md #31](../trackers/sevindu/4-planning-data.md#L31) | T18.3 | Checked | T18.3 [API] Monthly bill total exposed for budgets/planning (T31) |
| [trackers/sevindu/4-planning-data.md #32](../trackers/sevindu/4-planning-data.md#L32) | T18.4 | Open | T18.4 [AND·Sahan] Bills list with paid toggle and next due date |
| [trackers/sevindu/4-planning-data.md #36](../trackers/sevindu/4-planning-data.md#L36) | T18 Done when 1 | Checked | Tests: February due dates, month rollover resets unpaid, paid twice → 409 |
| [trackers/sevindu/4-planning-data.md #50](../trackers/sevindu/4-planning-data.md#L50) | T19.1 | Checked | T19.1 [API] CRUD `/api/v1/goals` (target > 0, target date in future) |
| [trackers/sevindu/4-planning-data.md #51](../trackers/sevindu/4-planning-data.md#L51) | T19.2 | Checked | T19.2 [API] Contributions add/delete; progress = sum(contributions) / target, capped display at 100% |
| [trackers/sevindu/4-planning-data.md #52](../trackers/sevindu/4-planning-data.md#L52) | T19.3 | Open | T19.3 [AND·Sahan] Goals list + detail with contributions |
| [trackers/sevindu/4-planning-data.md #56](../trackers/sevindu/4-planning-data.md#L56) | T19 Done when 1 | Checked | Tests: progress after edit/delete of contributions, invalid targets, no double counting |
| [trackers/sevindu/4-planning-data.md #69](../trackers/sevindu/4-planning-data.md#L69) | T33.1 | Checked | T33.1 [API] Snapshot job: store month's insights + health score once (idempotent upsert on user+month) |
| [trackers/sevindu/4-planning-data.md #70](../trackers/sevindu/4-planning-data.md#L70) | T33.2 | Checked | T33.2 [API] Recalculate a month when its transactions change |
| [trackers/sevindu/4-planning-data.md #71](../trackers/sevindu/4-planning-data.md#L71) | T33.3 | Checked | T33.3 [API] `GET /api/v1/history?from&to` → score + key insights per month |
| [trackers/sevindu/4-planning-data.md #72](../trackers/sevindu/4-planning-data.md#L72) | T33.4 | Checked | T33.4 [AND·Sahan] History screen: score trend chart + month list |
| [trackers/sevindu/4-planning-data.md #75](../trackers/sevindu/4-planning-data.md#L75) | T33 Done when 1 | Checked | Tests: running snapshot twice = same rows; editing an old transaction updates that month's score |
| [trackers/sevindu/5-alerts-data-rights.md #9](../trackers/sevindu/5-alerts-data-rights.md#L9) | T34.1 | Open | T34.1 [API] `POST/DELETE /api/v1/devices` register/remove FCM token |
| [trackers/sevindu/5-alerts-data-rights.md #10](../trackers/sevindu/5-alerts-data-rights.md#L10) | T34.2 | Open | T34.2 [API] Daily job creates events: budget NEAR/OVER, bill due in 2 days, goal date near; one alert per event (no duplicates) |
| [trackers/sevindu/5-alerts-data-rights.md #11](../trackers/sevindu/5-alerts-data-rights.md#L11) | T34.3 | Open | T34.3 [API] Send via FCM (Firebase Admin SDK, key from env); lock-screen text has no amounts |
| [trackers/sevindu/5-alerts-data-rights.md #12](../trackers/sevindu/5-alerts-data-rights.md#L12) | T34.4 | Open | T34.4 [API] User opt-out per alert type in `/me` |
| [trackers/sevindu/5-alerts-data-rights.md #13](../trackers/sevindu/5-alerts-data-rights.md#L13) | T34.5 | Open | T34.5 [AND·Sahan] FCM setup, notification channel, Android 13+ permission request, opt-out toggles |
| [trackers/sevindu/5-alerts-data-rights.md #16](../trackers/sevindu/5-alerts-data-rights.md#L16) | T34 Done when 1 | Open | Tests: job run twice sends once; opted-out user gets nothing; permission refused → app still works |
| [trackers/sevindu/5-alerts-data-rights.md #24](../trackers/sevindu/5-alerts-data-rights.md#L24) | T35.1 | Checked | T35.1 [API] `GET /api/v1/me/export` → JSON (or ZIP of CSVs) of all the user's own data; documented in `docs/api-contract.md` |
| [trackers/sevindu/5-alerts-data-rights.md #25](../trackers/sevindu/5-alerts-data-rights.md#L25) | T35.2 | Checked | T35.2 [API] Stream in memory, no temp files left; safe filename `finora-export-YYYY-MM-DD` |
| [trackers/sevindu/5-alerts-data-rights.md #26](../trackers/sevindu/5-alerts-data-rights.md#L26) | T35.3 | Checked | T35.3 [AND·Sahan] Download button → save via Storage Access Framework |
| [trackers/sevindu/5-alerts-data-rights.md #29](../trackers/sevindu/5-alerts-data-rights.md#L29) | T35 Done when 1 | Checked | Tests: export contains every owned row and nothing of another user; unauthenticated → 401; no data in logs |
| [trackers/sevindu/5-alerts-data-rights.md #37](../trackers/sevindu/5-alerts-data-rights.md#L37) | T36.1 | Checked | T36.1 [API] `DELETE /api/v1/me` with password confirmation → cascade delete all rows, revoke tokens, remove device tokens |
| [trackers/sevindu/5-alerts-data-rights.md #38](../trackers/sevindu/5-alerts-data-rights.md#L38) | T36.2 | Checked | T36.2 [API] Audit event `USER_DELETED` with no personal or financial payload |
| [trackers/sevindu/5-alerts-data-rights.md #39](../trackers/sevindu/5-alerts-data-rights.md#L39) | T36.3 | Checked | T36.3 Decide and record backup retention (e.g. backups expire in 30 days) in `docs/decisions.md` |
| [trackers/sevindu/5-alerts-data-rights.md #40](../trackers/sevindu/5-alerts-data-rights.md#L40) | T36.4 | Checked | T36.4 [AND·Sahan] Two-step confirm screen; clear local storage after success |
| [trackers/sevindu/5-alerts-data-rights.md #43](../trackers/sevindu/5-alerts-data-rights.md#L43) | T36 Done when 1 | Checked | Tests: after delete no rows remain for that user; old tokens → 401; same email can register again |
| [trackers/sevindu/6-admin-staff.md #9](../trackers/sevindu/6-admin-staff.md#L9) | T41.1 | Checked | T41.1 [API] Seed first ADMIN from env vars on startup (no hard-coded password) |
| [trackers/sevindu/6-admin-staff.md #10](../trackers/sevindu/6-admin-staff.md#L10) | T41.2 | Checked | T41.2 [API] `GET /api/v1/admin/stats` → user counts by status, staff count, open tickets, active SMS formats (no financial data) |
| [trackers/sevindu/6-admin-staff.md #11](../trackers/sevindu/6-admin-staff.md#L11) | T41.3 | Checked | T41.3 [AND] Admin home screen with these counts and links to T42–T45 |
| [trackers/sevindu/6-admin-staff.md #14](../trackers/sevindu/6-admin-staff.md#L14) | T41 Done when 1 | Checked | Tests: USER/STAFF tokens and forged/expired tokens → 401/403; response contains no amounts |
| [trackers/sevindu/6-admin-staff.md #22](../trackers/sevindu/6-admin-staff.md#L22) | T42.1 | Checked | T42.1 [API] `GET /api/v1/admin/users?q=` (email/name search) → id, email, name, status, created_at only |
| [trackers/sevindu/6-admin-staff.md #23](../trackers/sevindu/6-admin-staff.md#L23) | T42.2 | Checked | T42.2 [API] `PUT /api/v1/admin/users/{id}/status` `{status, reason}` → audit event; disabling revokes tokens |
| [trackers/sevindu/6-admin-staff.md #24](../trackers/sevindu/6-admin-staff.md#L24) | T42.3 | Checked | T42.3 [AND] User list + enable/disable dialog with reason |
| [trackers/sevindu/6-admin-staff.md #27](../trackers/sevindu/6-admin-staff.md#L27) | T42 Done when 1 | Checked | Tests: disabled user cannot log in; admin has no endpoint returning transactions/budgets/goals |
| [trackers/sevindu/6-admin-staff.md #35](../trackers/sevindu/6-admin-staff.md#L35) | T43.1 | Checked | T43.1 [API] `POST /api/v1/admin/staff` create STAFF (temporary password, must change at first login) |
| [trackers/sevindu/6-admin-staff.md #36](../trackers/sevindu/6-admin-staff.md#L36) | T43.2 | Checked | T43.2 [API] Disable/enable staff; role field cannot be set to ADMIN by any endpoint |
| [trackers/sevindu/6-admin-staff.md #37](../trackers/sevindu/6-admin-staff.md#L37) | T43.3 | Checked | T43.3 [AND] Staff list + create/disable |
| [trackers/sevindu/6-admin-staff.md #40](../trackers/sevindu/6-admin-staff.md#L40) | T43 Done when 1 | Checked | Tests: staff cannot create staff/admins or change own role; disabled staff session → 403 |
| [trackers/sevindu/6-admin-staff.md #48](../trackers/sevindu/6-admin-staff.md#L48) | T44.1 | Checked | T44.1 [API] CRUD defaults (`user_id NULL`) with EN + SI names; deactivate instead of delete when in use |
| [trackers/sevindu/6-admin-staff.md #49](../trackers/sevindu/6-admin-staff.md#L49) | T44.2 | Checked | T44.2 [API] Unique name per kind; user-owned categories never changed |
| [trackers/sevindu/6-admin-staff.md #50](../trackers/sevindu/6-admin-staff.md#L50) | T44.3 | Checked | T44.3 [AND] Category management screen |
| [trackers/sevindu/6-admin-staff.md #53](../trackers/sevindu/6-admin-staff.md#L53) | T44 Done when 1 | Checked | Tests: USER cannot edit defaults; deactivated default stays on old transactions |
| [trackers/sevindu/6-admin-staff.md #61](../trackers/sevindu/6-admin-staff.md#L61) | T45.1 | Checked | T45.1 [API] CRUD versioned formats per bank (pattern + field mapping); only one active version per bank |
| [trackers/sevindu/6-admin-staff.md #62](../trackers/sevindu/6-admin-staff.md#L62) | T45.2 | Checked | T45.2 [API] Validate a format against redacted sample text before activating |
| [trackers/sevindu/6-admin-staff.md #63](../trackers/sevindu/6-admin-staff.md#L63) | T45.3 | Checked | T45.3 [API] `GET /api/v1/sms-formats` (USER) → active formats for the app's local parser (T14) |
| [trackers/sevindu/6-admin-staff.md #64](../trackers/sevindu/6-admin-staff.md#L64) | T45.4 | Checked | T45.4 [API] Roll back = activate an older version |
| [trackers/sevindu/6-admin-staff.md #65](../trackers/sevindu/6-admin-staff.md#L65) | T45.5 | Checked | T45.5 [AND] Format list, edit, test-sample, activate/rollback |
| [trackers/sevindu/6-admin-staff.md #68](../trackers/sevindu/6-admin-staff.md#L68) | T45 Done when 1 | Checked | Tests: invalid pattern rejected; rollback restores previous; T16 regression set still passes after a change |
| [trackers/sevindu/6-admin-staff.md #80](../trackers/sevindu/6-admin-staff.md#L80) | T46.1 | Checked | T46.1 [API] `GET /api/v1/staff/stats` → my open tickets, unassigned tickets |
| [trackers/sevindu/6-admin-staff.md #81](../trackers/sevindu/6-admin-staff.md#L81) | T46.2 | Checked | T46.2 [AND·Sahan] Staff home screen |
| [trackers/sevindu/6-admin-staff.md #84](../trackers/sevindu/6-admin-staff.md#L84) | T46 Done when 1 | Checked | Tests: STAFF → `/admin/**` 403; STAFF → user finance endpoints 403; disabled staff → 403 |
| [trackers/sevindu/6-admin-staff.md #92](../trackers/sevindu/6-admin-staff.md#L92) | T47.1 | Checked | T47.1 [API] USER: `POST /api/v1/support/tickets` (subject, body, optional error_code), list own |
| [trackers/sevindu/6-admin-staff.md #93](../trackers/sevindu/6-admin-staff.md#L93) | T47.2 | Checked | T47.2 [API] STAFF: list unassigned/assigned, claim, reply, change status |
| [trackers/sevindu/6-admin-staff.md #94](../trackers/sevindu/6-admin-staff.md#L94) | T47.3 | Checked | T47.3 [API] Strip/reject text that looks like card or account numbers; no attachments in v1 |
| [trackers/sevindu/6-admin-staff.md #95](../trackers/sevindu/6-admin-staff.md#L95) | T47.4 | Checked | T47.4 [AND·Sahan] User "Help" form + staff ticket screens |
| [trackers/sevindu/6-admin-staff.md #98](../trackers/sevindu/6-admin-staff.md#L98) | T47 Done when 1 | Checked | Tests: user sees only own tickets; staff can reply only to claimed tickets; account-number text redacted |
| [trackers/sevindu/7-testing-release.md #31](../trackers/sevindu/7-testing-release.md#L31) | T56.1 | Open | T56.1 Ubuntu 22.04 server: firewall (22, 443 only), non-root service user, systemd unit for the API jar |
| [trackers/sevindu/7-testing-release.md #32](../trackers/sevindu/7-testing-release.md#L32) | T56.2 | Open | T56.2 MySQL bound to localhost, app user with least privileges |
| [trackers/sevindu/7-testing-release.md #33](../trackers/sevindu/7-testing-release.md#L33) | T56.3 | Open | T56.3 Nginx + Let's Encrypt HTTPS reverse proxy; secrets in env file readable only by service user |
| [trackers/sevindu/7-testing-release.md #34](../trackers/sevindu/7-testing-release.md#L34) | T56.4 | Open | T56.4 Nightly encrypted `mysqldump` backup, keep per T36.3; do one restore drill |
| [trackers/sevindu/7-testing-release.md #35](../trackers/sevindu/7-testing-release.md#L35) | T56.5 | Open | T56.5 Signed release APKs (`v1` and `beta`); keystore never in Git |
| [trackers/sevindu/7-testing-release.md #36](../trackers/sevindu/7-testing-release.md#L36) | T56.6 | Checked | T56.6 Rollback steps in `docs/deploy.md` — §6 + `deploy/release.sh` (auto-rollback on failed health check) and `deploy/rollback.sh` |
| [trackers/sevindu/7-testing-release.md #39](../trackers/sevindu/7-testing-release.md#L39) | T56 Done when 1 | Open | HTTPS health check works from a phone; restore drill done and noted |
| [trackers/sevindu/7-testing-release.md #52](../trackers/sevindu/7-testing-release.md#L52) | T57.1 | Open | T57.1 On a real phone: register → add records/SMS import → dashboard → patterns → score → advice → daily guide/simulator |
| [trackers/sevindu/7-testing-release.md #53](../trackers/sevindu/7-testing-release.md#L53) | T57.2 | Open | T57.2 Admin: all 5 functions; Staff: all 4 functions |
| [trackers/sevindu/7-testing-release.md #54](../trackers/sevindu/7-testing-release.md#L54) | T57.3 | Open | T57.3 `beta` shows two unlabeled advices + ratings; `v1` shows one |
| [trackers/sevindu/7-testing-release.md #55](../trackers/sevindu/7-testing-release.md#L55) | T57.4 | Open | T57.4 List open defects; document only verified working features |
| [trackers/sevindu/7-testing-release.md #58](../trackers/sevindu/7-testing-release.md#L58) | T57 Done when 1 | Open | `docs/acceptance.md` filled with real results/screenshots |
| [trackers/thilina/1-metrics-patterns.md #9](../trackers/thilina/1-metrics-patterns.md#L9) | T21.1 | Checked | T21.1 `MetricsService.forMonth(userId, YearMonth)` → income, expense, needs, wants, fixed bills, savings, payday-window spend, weekend vs weekday spend (all BigDecimal) |
| [trackers/thilina/1-metrics-patterns.md #10](../trackers/thilina/1-metrics-patterns.md#L10) | T21.2 | Checked | T21.2 Every metric carries its source: amount, period, transaction count |
| [trackers/thilina/1-metrics-patterns.md #11](../trackers/thilina/1-metrics-patterns.md#L11) | T21.3 | Checked | T21.3 Edge cases: no income (ratios = null, not divide-by-zero), refunds/reversals, uncategorised wants/needs |
| [trackers/thilina/1-metrics-patterns.md #14](../trackers/thilina/1-metrics-patterns.md#L14) | T21 Done when 1 | Checked | Unit tests with hand-calculated fixtures (write the expected numbers in the test) |
| [trackers/thilina/2-score-rules.md #9](../trackers/thilina/2-score-rules.md#L9) | T23.1 | Checked | T23.1 Use the approved five-part formulas in `docs/decisions.md` D20; no new scoring thresholds |
| [trackers/thilina/2-score-rules.md #10](../trackers/thilina/2-score-rules.md#L10) | T23.2 | Checked | T23.2 Pure BigDecimal calculator + `ScoreService.forMonth()` → total/parts; points clamped to weights, total 0–100, missing total null, explicit N/A weights normalized |
| [trackers/thilina/2-score-rules.md #11](../trackers/thilina/2-score-rules.md#L11) | T23.3 | Checked | T23.3 Stable reason keys + aggregate numeric facts for EN/SI; debt paid markers and user-declared reserves explicitly labeled |
| [trackers/thilina/2-score-rules.md #12](../trackers/thilina/2-score-rules.md#L12) | T23.4 | Checked | T23.4 USER-only `GET /api/v1/score?month=YYYY-MM`; no user id accepted; validate month and preserve ownership/role privacy |
| [trackers/thilina/2-score-rules.md #13](../trackers/thilina/2-score-rules.md#L13) | T23.5 | Checked | T23.5 Minimal owner-only GET/PUT `/api/v1/score-inputs/{month}`: nullable reserve/date and no-applicable-debt/goals declarations, UTC confirmation time; V7 schema, validation and contradiction checks |
| [trackers/thilina/2-score-rules.md #14](../trackers/thilina/2-score-rules.md#L14) | T23.6 | Open | T23.6 Team review and remaining T18/T19 dependencies resolved before full task acceptance; no teammate status changed |
| [trackers/thilina/2-score-rules.md #17](../trackers/thilina/2-score-rules.md#L17) | T23 Done when 1 | Checked | Tests: zero income, no goals, no debt, perfect month, worst month; same input → same output |
| [trackers/thilina/4-planning-tools.md #9](../trackers/thilina/4-planning-tools.md#L9) | T31.1 | Open | T31.1 [API] `(balance until next income − unpaid bills − planned savings) / days left`; next income date from user setting or last income + 1 month |
| [trackers/thilina/4-planning-tools.md #10](../trackers/thilina/4-planning-tools.md#L10) | T31.2 | Open | T31.2 [API] Return assumptions used; negative → 0 with warning flag |
| [trackers/thilina/4-planning-tools.md #11](../trackers/thilina/4-planning-tools.md#L11) | T31.3 | Open | T31.3 [AND·Sahan] Daily guide card + assumptions sheet |
| [trackers/thilina/4-planning-tools.md #14](../trackers/thilina/4-planning-tools.md#L14) | T31 Done when 1 | Open | Tests: last day of month, income today, no income history, negative balance |
| [trackers/thilina/4-planning-tools.md #23](../trackers/thilina/4-planning-tools.md#L23) | T32.1 | Open | T32.1 [API] `POST /api/v1/simulate` `{goalId, monthlyChange, categoryCuts[]}` → new finish date, monthly amount; nothing saved |
| [trackers/thilina/4-planning-tools.md #24](../trackers/thilina/4-planning-tools.md#L24) | T32.2 | Open | T32.2 [AND·Sahan] Simulator screen, clearly marked "Simulation" |
| [trackers/thilina/4-planning-tools.md #27](../trackers/thilina/4-planning-tools.md#L27) | T32 Done when 1 | Open | Tests: rounding, zero change, impossible goal |
| [trackers/thilina/5-qa.md #9](../trackers/thilina/5-qa.md#L9) | T52.1 | Open | T52.1 Matrix test: every endpoint × {none, USER, other USER, STAFF, ADMIN, disabled} → expected status |
| [trackers/thilina/5-qa.md #10](../trackers/thilina/5-qa.md#L10) | T52.2 | Open | T52.2 ID-guessing tests on every `{id}` endpoint |
| [trackers/thilina/5-qa.md #11](../trackers/thilina/5-qa.md#L11) | T52.3 | Open | T52.3 Confirm Admin/Staff responses never include amounts or transactions |
| [trackers/thilina/5-qa.md #14](../trackers/thilina/5-qa.md#L14) | T52 Done when 1 | Open | Matrix test green in CI |
| [trackers/thilina/5-qa.md #28](../trackers/thilina/5-qa.md#L28) | T53.1 | Open | T53.1 Verify HTTPS, BCrypt hashes, token expiry, redacted logs |
| [trackers/thilina/5-qa.md #30](../trackers/thilina/5-qa.md#L30) | T53.2 | Open | T53.2 Capture app traffic (proxy) during SMS import: no SMS body; capture LLM request: no forbidden fields |
| [trackers/thilina/5-qa.md #31](../trackers/thilina/5-qa.md#L31) | T53.3 | Open | T53.3 Run OWASP dependency-check + secret scan (e.g. gitleaks); fix high findings |
| [trackers/thilina/5-qa.md #34](../trackers/thilina/5-qa.md#L34) | T53 Done when 1 | Open | `docs/security-checklist.md` filled with real results |
| [trackers/thilina/5-qa.md #63](../trackers/thilina/5-qa.md#L63) | T55.1 | Checked | T55.1 SMS field accuracy from T16 ≥ 90% |
| [trackers/thilina/5-qa.md #64](../trackers/thilina/5-qa.md#L64) | T55.2 | Open | T55.2 Seed one year of data; measure main screens < 2 s on a mid-range phone |
| [trackers/thilina/5-qa.md #65](../trackers/thilina/5-qa.md#L65) | T55.3 | Open | T55.3 SUS questionnaire with trial users; target ≥ 70 |
| [trackers/thilina/5-qa.md #66](../trackers/thilina/5-qa.md#L66) | T55.4 | Open | T55.4 Beta advice ratings: compare RULE vs AI with a statistical test |
| [trackers/thilina/5-qa.md #69](../trackers/thilina/5-qa.md#L69) | T55 Done when 1 | Open | `docs/evaluation.md` contains real measured numbers |

## Fresh verification and delivery state

- GitHub API confirmed PR #16 is open, draft and unmerged. Requested reviewers are SEVINTINE7 and ThilinaW-LK. The reviews endpoint returned zero records. CI run [45477952627](https://github.com/SEVINTINE7/FINORA/actions/runs/45477952627) returned success for both API and Android at the baseline SHA.
- Android Gradle reported `BUILD SUCCESSFUL in 1m 5s` for debug, unsigned release, instrumentation assembly and lint. A separate forced JVM test run reported `BUILD SUCCESSFUL in 5s`. Fresh XML totals were 146 tests, 0 failures, 0 errors and 0 skipped. Lint reported `No issues found.`
- Release APK inspection reported app ID `lk.finora.app`, min SDK 26, target SDK 37, backup disabled, no debuggable flag and no READ_SMS permission. The packaged network resource reported `cleartextTrafficPermitted=0`. Actual HTTPS authentication and complete packaged security review remain open.
- The 460 English and 460 Sinhala entries had matching resource keys and checked string formatter placeholders. Review of plural quantities, translation approval inventories and device accessibility remains open.
- The repository proposal DOCX contains a title page. The full original proposal is still needed to independently verify requirements that cite it by section.
- Initial backend run returned `Tests run: 353, Failures: 0, Errors: 252, Skipped: 0` after MySQL connection failures. After starting a disposable loopback-only MySQL 8 container, `mvnw.cmd -B verify` reported `Tests run: 353, Failures: 0, Errors: 0, Skipped: 0`, `All coverage checks have been met`, and `BUILD SUCCESS`. Flyway applied V1–V22. `SchemaMigrationTest` ran 12 cases and `FinanceWriteConcurrencyTest` ran 27, each with zero failures, errors or skips. Spring Boot repackaged the JAR. The disposable database was stopped. The V21-to-V22 representative existing-data upgrade remains historical evidence.

## Resume from this checkout

The register contains 220 checkbox rows, including parent tasks and T21/T23. Expand each in-scope row into an evidence record as the source, assertions and runtime behavior are reviewed. Compare the exact localization approvals and retained HMD evidence against the current revision. Continue with isolated emulator journeys, authorization and privacy coverage, synthetic existing-data upgrades, integrated candidate builds and the three physical planning checks after prerequisites permit. Reconcile all five MD trackers and INDEX only when criterion evidence supports a change. Historical MD totals (23/23, 16/16, 9/9, 20/20, 15/16) and delegated totals (22/22) have not been independently certified by this checkpoint.

## Targeted emulator and retained evidence review

- Android 17 read-only FINORA_D54 emulator installed the fresh debug and instrumentation APKs. Direct isolated instrumentation for `UserJourneyTest#loginAndLanguageDialogUseSecureWindows` returned `OK (1 test)` in 8.028 s. The isolated emulator was shut down. This verifies those two secure windows and leaves the broader current UI journeys open.
- The retained D50 evidence records three HMD consent test scenarios and a separate Sinhala persistence check. The later listening evidence records Sahan's human confirmation of scoped USER and STAFF labels in both languages. Source paths for app resources, Android UI, and backend user flows show no changes from `80d5c20` to the audited baseline. These are historical handset observations.
- The T51 financial journey asserts the dashboard expense after creation, remaining budget after creation, edited amount in the transaction list, empty list after deletion, and denied SMS permission after exiting the permission screen. The journey source was inspected. Only the secure-window method was freshly executed here.

## Interim team handoff

Sevindu and Thilina, fresh baseline verification at `265b92e3946a5c528c374b1dc4c67f7437091fb6` passed the 353-test MySQL suite and 146 Android JVM tests. Both CI jobs succeeded. [PR #16](https://github.com/SEVINTINE7/FINORA/pull/16) is still draft with both reviews requested. Criterion-level coverage and acceptance reconciliation are still in progress. Thilina owns T21/T23 semantic-code adoption and downstream checks, plus planning/evaluation coordination. The planning and QA tracker overlaps need reconciliation before integration. Sevindu owns the applicable formal planning prerequisites and the T56 HTTPS environment. Sahan supplies the pending physical planning evidence and authenticated release verification once prerequisites are available. Exact-revision independent review is still required for final delivery.

## Continuation findings for T13 and T39

The review branch remains at `265b92e3946a5c528c374b1dc4c67f7437091fb6`. Repository history yielded the original `FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md` and the original per-member trackers in `b8fffee`. The proposal DOCX in that commit and the checkout is still the title page. The full proposal remains an evidence gap, including the original wording of §7.3.

| Criterion | Source, assertions and evidence | Disposition |
| --- | --- | --- |
| T13.1 | Debug manifest declares the permission. Prior release APK inspection confirms omission. D30 and the T13 handoff document sideload-only scope and Android installer allowlisting. | Implementation and handoff supported. Original proposal §7.3 requires the full document. |
| T13.2 | `activity_sms_access.xml` places the explanation ahead of actions. `SmsAccessActivity` launches the request from Allow and finishes from No thanks. `SmsPermissionStateTest` asserts which states may prompt. | Source supported. The JVM test covers eligibility. Historical HMD observations cover the actual UI. |
| T13.3 | The three fallback controls are outside permission visibility changes. Their listeners open Paste, CSV and Manual through the verified session guard. | Source supported. Older HMD navigation and isolated paste execution are retained. Current end-to-end journey remains open. |
| T13.4 | `SmsPermissionStateTest` asserts Settings for an earlier request with no rationale and recomputes grant or revocation. Activity uses the app-details intent and rereads permission on resume. | Source and assertions supported. Under D34, combine retained HMD App info evidence and isolated emulator revocation and regrant. The current integrated path remains to be exercised. |
| T13 Done when 1 | The historical HMD run covered grant, ordinary refusal and permanent refusal. The current layout and activity differ from the D34 emulator revision. | Retain historical evidence. Compare intermediate changes and finish current runtime verification before full acceptance. |
| T39.1 | Retained lint reported no issues. Targeted Java setter and layout attribute scan found no literal label in those entry points. | Static evidence supported within the scan. Complete dynamic and alternate rendering paths before full acceptance. |
| T39.2 | `LocaleHelper` applies AppCompat locale and stores the last server-confirmed value per login. `LocaleHelperTest` checks read-before-write, same-language no-op, failed confirmation and login cache isolation. | Normal synchronization supported. Concurrent profile name edits expose the defect below. |
| T39.3 | Historical HMD samples cover Login, role homes and the five USER tabs at the recorded settings. | Retained scoped evidence. Current full-screen and large-font coverage remains open. |
| T39.4 | Current EN and SI XML entries match the pinned values for the 96, 64, 23 and 106 approved application pairs, with zero differences in either locale. Both plural resources have matching `one` and `other` quantities. | Scoped approvals confirmed. Audit the original 37-pair inventory and map all applicable current Sahan-owned strings to approvals. |
| T39 Done when 1 | T39 handoff records audible TalkBack for Sign in, Show password and Language. MD05 adds scoped FAQ and Support observations. | Retain the recorded observations. They do not establish a bilingual walkthrough of every current screen. |

**Open T39.2 and T07.5 data integrity defect.** The locale change reads `fullName` and then submits it with the new language. `MeService` writes both fields unconditionally. If a second authorized session saves a profile name between the two calls, the later language update can overwrite it with the old name. The existing test asserts reuse of the name from the first response and does not cover this ordering. Reproduce with a deterministic interleaving, then coordinate a language-only update or a conditional update with the owner of the profile endpoint. Verify ordinary profile edits and authorized language updates across both sessions.

T17.3 and T19.3 retain T10 as an open formal prerequisite. T18.4 retains T17. No handset request was issued. Git remote heads still match the prior checkpoint. The retained baseline suites were not rerun in this continuation.

**Exact remaining rows.** All rows in the 220-row register other than the ten partial dispositions above still need an independent criterion-level disposition. The ten listed rows retain the specific open checks in their dispositions. No overall count is certified.

## Continuation findings for T07 and T37

History search across available Git refs for proposal, requirements, PDF and DOCX paths found the proposal title page and the later MD05 owner proposal. The original lean tracker and ownership trackers are available. The full academic proposal remains an evidence gap.

| Criterion | Sahan applicability and evidence | Disposition |
| --- | --- | --- |
| T07.1 | Sevindu owns the registration API. Android supplies the required inputs. `MeControllerTest.registerSavesConsentRow` asserts version 1.0, adult flag, a populated acceptance timestamp and a null withdrawal timestamp. | Backend dependency supported by retained MySQL verification. |
| T07.2 | Sevindu owns the profile contract. Sahan integrates it. `MeController` derives identity from the current principal. Tests assert response fields, absence of password hash, 401 for no token, persistence of both updated fields and validation errors. | Contract supported. The non-atomic Android read and write sequence is covered by the defect below. |
| T07.3 | Sevindu owns withdrawal and restricted recovery. Sahan integrates both into Android under D53/D54. `MeService` acquires the shared owner lock, marks withdrawal and invalidates sessions. Controller assertions cover old refresh and access rejection, restricted use, replay, expiry, version and fresh sign-in. | Source and retained tests support the scoped dependency. |
| T07.4 | Sahan owns registration UI. `RegisterActivity` checks both boxes and busy state before enabling or executing submit. The bilingual instrumentation method checks all controls, each single-checkbox state and the both-checked state. | Supported by source, assertions and retained isolated HMD evidence. |
| T07.5 | Sahan owns Settings integration. `SettingsActivity` wires profile locale, withdrawal, export and password-confirmed deletion. Retained HMD evidence covers the normal flows. | Partial. Language update can overwrite a newer name. Locale is applied after server confirmation. |
| T07 Done when 1 | API dependency. `MeControllerTest` covers GET, PUT and withdrawal with both old credentials rejected. | Supported by assertions and retained MySQL run. |
| T07 Done when 2 | Sahan owns device evidence. The retained D50 HMD run exercised both consent checkboxes and the Settings switch to Sinhala, including persisted server language. | Supported by retained evidence for the requested device actions. |
| T37.1 | Sahan owns the scaffold. Gradle sets Java 17, the required package, min SDK 26 and target SDK 37. Both wrapper launchers, properties and JAR are tracked. Android 17 is a released platform. | Supported by source, retained APK inspection and the [Android 17 release](https://developer.android.com/blog/posts/android-17-is-here). |
| T37.2 | Sahan owns V1 packaging. Gradle defines one advice flavor and one distribution application ID. D50's opt-in suffix is isolated to the guarded debug build. | Supported by Gradle and inspected release APK. |
| T37.3 | Sahan owns debug networking. Gradle sets device loopback and debug XML permits cleartext only there. Release XML denies cleartext and Gradle requires an HTTPS origin for signing. | Supported by source, APK evidence and retained reverse mapping. Actual release authentication remains T40.1/T56. |
| T37.4 | Sahan owns Android networking. `ApiClient` configures Retrofit and Gson. `AuthInterceptorTest` asserts Bearer on the configured origin and no header on another origin. `ApiClientTest` checks error code, status and field details. | Supported by implementation and retained JVM assertions. |
| T37.5 | Sahan owns the app structure. Feature UI, repositories, API and local packages are present. Login uses a ViewModel and LiveData. `TokenStore` opens EncryptedSharedPreferences with AES256 key and value schemes. | Supported by source and retained app evidence. |
| T37.6 | Sahan owns shared status UI. `StateView` renders localized loading, empty and error resource IDs. Both locale files are present, and the first implementation commit introduced the resource structure. | Supported by source and resource evidence. |
| T37.7 | Sahan owns login UI. The view and ViewModel call the login repository. The MockWebServer test asserts POST method, route, exact email and password fields, absent Authorization header and parsed response. | Supported by implementation, assertions and retained handset sign-in. |
| T37.8 | Sahan owns Android workflow integration. CI invokes `assembleV1Debug testV1DebugUnitTest`. | Supported by workflow and retained baseline CI. |
| T37 Done when 1 | GitHub Jobs API returned Android success at `c20087e3320d2f08a01ad04039edfa075ea28006`. | Verified from the [recorded run](https://github.com/SEVINTINE7/FINORA/actions/runs/44940716004). |
| T37 Done when 2 | Existing handoff records the authorized Android 14 installation, sign-in, reverse route and encrypted restart. Prior force-stop instrumentation reopened both tokens and called `/me`. | Supported by retained device evidence. |
| T37 Done when 3 | Inspected V1 APK application ID and Gradle flavor agree with D03. | Supported. |

### Profile update proposal for Sevindu

T07.2 is Sevindu-owned. D53 delegates the integration links for T07.5 and data rights, and D54 delegates restricted recovery. Neither records approval to change the general profile write contract. Preserve the existing `PUT /api/v1/me` semantics. Propose adding `PATCH /api/v1/me` with one required EN/SI language field, the existing USER principal binding, a column-scoped language update, and the existing `MeResponse`. Reuse the owner lock for the change and make the full PUT follow the same lock before reading the entity so concurrent full and partial writes are serialized. Move the Android selector to the language-only contract. Keep the existing request-generation and confirmed-response checks.

Regression scenario in an isolated MySQL fixture: two authorized sessions for the same USER. A reads the original name. B changes it through the full PUT. A saves Sinhala through the proposed PATCH. The response and a new GET must show the newer name and Sinhala. Also verify the original two-field PUT, invalid and missing language, current USER binding and consent withdrawal behavior. On Android, assert that the dedicated request sends only language and an unconfirmed result leaves the previously confirmed locale in place.

**Coverage after this pass.** The ten T13/T39 rows above and the eighteen T07/T37 rows here have individual dispositions. Every other criterion ID in the original register is pending. The four tracker statuses remain unchanged. No suites were repeated, and there were no source changes in this pass.

## Component audit for sessions, manual entry and SMS

All rows in this batch are Sahan implementation scope except T08.5 and the original T15.3 API responsibility. T08.5 is consumed by Android and T15.3 has D38 scoped delegation.

| Criterion | Evidence and disposition |
| --- | --- |
| T08.1 | SessionNavigation maps all three roles to separate homes with protected entry. Retained HMD evidence supports routing. |
| T08.2 | SessionAuthenticator gates 401s and prior responses. Tests count one refresh and one replay, assert credential rotation, retain session on temporary failure, clear on definitive rejection and prevent a queued call using a replacement account. Supported by retained tests. |
| T08.3 | HomeViewModel calls local account clear and server logout with the revocation snapshot. Session tests assert logout prevents stale publication and revokes the late rotated credential. Retained encrypted restart evidence supports restoration. |
| T08.4 | LoginActivity maps both repository results to localized resources. LoginRepositoryTest asserts each result and zero token writes. Supported. |
| T08.5 | AuthControllerTest asserts USER role and SI language in the actual login response. Supported integration dependency. |
| T08 Done when 1 | Retained three-role handset routing supports the device criterion. |
| T08 Done when 2 | Retained device evidence supports expiry, disabled feedback and USER intent denial. Keep distinct from the automated refresh assertions. |
| T11.1 | TransactionFormActivity binds amount, date picker, merchant, note and account/category/type. TransactionFormTest verifies exact decimal and original archived-account rules. Retained HMD create/edit evidence supports the flow. |
| T11.2 | TransactionFormValidator rejects incomplete or mismatched data, invalid monetary syntax and overprecision. Tests assert seven errors and no request for the incomplete fixture. Activity renders per-field feedback. Supported. |
| T11.3 | Draft lives in ViewModel, rotation captures input, load has Retry, and failed creates retain the draft with review and confirmation gating. Unit test asserts both gates. Retained emulator evidence supports rotation during held and failed requests. |
| T11.4 | Records exposes Manual entry independently of permission, and the destination is non-exported and session-bound. Retained HMD and Espresso evidence support access with SMS denied. |
| T11 Done when 1 | Validation assertions and retained isolated device rotation/airplane tests support this clause. HMD rotation itself remains unobserved. |
| T14.1 | Inbox reader test checks parameterized sender and date interval, row cap and provider-row rechecks, plus no query without permission or formats. Supported. |
| T14.2 | Remote format assertions cover approved snapshot, authenticated bodyless GET, malformed/offline cache fallback, empty remote disablement and login replacement. Supported within D35/D36. |
| T14.3 | SmsAccuracyTest compares four typed fields per approved NSB variant and asserts manual review for unsupported or malformed messages. Supported. |
| T14.4 | Preview constructs outbound DTO from confirmed structured fields, original digest and independent selections. Retained batch capture and process-log evidence found no raw fixture text. Supported for inspected routes. |
| T14 Done when 1 | Approved NSB positives and unknown-format manual-review assertions present in retained Android suite. Supported. |
| T14 Done when 2 | Retained capture covered request bodies on the relevant batch and format paths. Supported as historical network evidence. |
| T15.1 | Preview editor exposes edits and selection. SmsPreviewDraftTest asserts exact edited amount, date, merchant, account/category and deselection. Supported with retained emulator evidence. |
| T15.2 | Test pins a SHA-256 vector, equality across inbox and paste and a stable original digest after edits. Retained batch UI reported possible duplicates. Supported under D38. |
| T15.3 | SmsBatchControllerTest asserts one stored row across inbox/paste/repeats, preserves a manual match and validates the batch before writes. Supported within D38 delegation. |
| T15.4 | SmsPendingBatchTest asserts structured batch retention within login and clearing on replacement/disposal. Preview explicitly confirms retry. Retained lost-response and rotation runs support behavior. |
| T15 Done when 1 | MySQL assertions cover duplicate and manual-match storage, and retained emulator capture shows no automatic replay and zero saves on explicit identical retry. Supported. |
| T16.1 | SmsAccuracyTest loads the D35 fixture file and expects one row per approved synthetic variant. Supported under D38. |
| T16.2 | Approved debit and credit are positives. Reversal and ATM are unsupported NSB variants and, along with malformed text, must require manual review. Retained test covers these outcomes. |
| T16.3 | Test counts four expected fields per positive and asserts the 90 percent threshold. Retained 12/12 evidence supports the approved synthetic set. |
| T16 Done when 1 | T16 handoff records actual retained 12/12 output and scope. Supported. |

**Coverage after this batch.** 55 of 220 register rows have individual dispositions. This includes 48 of the 84 original MD rows. Remaining IDs are all register rows outside T07, T08, T11, T13–T16, T37 and T39. Open caveats on examined rows remain in their dispositions.

## Component audit for financial screens, privacy and staff assistance

| Criterion | Evidence and disposition |
| --- | --- |
| T20.1 | Sevindu owns the dashboard API. DashboardControllerTest checks exact totals, per-category amounts, null category, six ordered buckets and isolation. Supports Sahan's client dependency. |
| T20.2 | DashboardPanel renders month navigation, totals, PieChart and BarChart with text alternatives. Supported by retained HMD fixture and month-change runs. |
| T20.3 | API test asserts zeros and empty categories. DashboardPanel renders the localized empty message. Supported. |
| T20 Done when 1 | Test compares fixture totals and net and independently verifies expense against a raw SQL sum. Supported by retained MySQL suite. |
| T20 Done when 2 | Retained mid-range HMD viewport timings of 505/504/486 ms cover warm Records-to-Home with 27 synthetic records across 12 months. Supported within that measured scenario. |
| T38.1 | MainActivity wires the five destinations and their implemented links. Retained device and recreation evidence supports navigation. |
| T38.2 | Account, category, records, CSV, SMS, planning CRUD, Insights, History, Settings, Help and Advice routes are wired. Pending planning and alerts have separate criteria. Supported for D40 scope. |
| T38.3 | Retained emulator runs verified account/category/transaction/budget/bill/goal refresh after mutations and dependent Home and Insights refresh. Supported for exercised lists. |
| T38 Done when 1 | Retained HMD navigation and emulator mutation checklist support implemented routes. Future integration stays separately open. |
| T24.1 | Insights client validates pattern response and renders amount, ratio and period. HMD fixture exercised all four patterns. Supported subject to D55 downstream totals. |
| T24.2 | InsightsViewModel validates parts, reason keys and unavailable totals. Retained HMD evidence covers totals 47/52, not-applicable parts and zero-income unavailability. Supported for fixture. |
| T24.3 | Guidance copy is localized and displayed on the Insights screen. Retained review and fixture evidence support it. |
| T24 Done when 1 | HMD displayed fixture figures and periods. Original fixture agreement supported. D55 rename regression remains open downstream. |
| T30.1 | Thilina owns API behavior and D41 delegates scoped integration. Retained verifier and delivery tests support verified-or-RULE semantics. Live-provider evidence remains conditional under D44. |
| T30.2 | Delivery/rating tests cover ownership, role, rating persistence and concurrent 201/409. Supported scoped dependency. |
| T30.3 | Advice ViewModel accepts only verified AI or RULE fallback and bounds response data before rendering. Retained device run exercised RULE and rating recovery. |
| T30.4 | AdviceActivity displays one V1 advice experience, supporting values and one rating state. Retained HMD and emulator evidence support the delivered path. |
| T30.5 | Advice mode comes from the V1 build configuration. The advice UI has no mode selector. Supported. |
| T30 Done when 1 | Retained duplicate rating and verifier fallback assertions support the requirement. |
| T30 Done when 2 | Retained HMD displayed exact LKR 50.23 and reconciled a dropped rating response with one POST and a read. Supported for exercised device path. |
| T40.1 | Baseline APK inspection supports the static controls. Authenticated release remains deferred under D51/D56/T56. Keep open. |
| T40.2 | Manifest disables backup and SecureActivity sets FLAG_SECURE for screens and dialogs. Retained capture blocking and fresh emulator login/dialog assertion support exercised surfaces. |
| T40.3 | Device evidence supports connection recovery, process restart and expiry. Retained rotation checks support the approved isolated geometry scenarios. |
| T40 Done when 1 | Retained HMD checklist and app-process Logcat provide scoped synthetic-token/SMS evidence. Release-specific runtime remains under T40.1. |
| T48.1 | STAFF catalog/preview and retained two-language reply lifecycle cover approved templates and insertion. Supported. |
| T48.2 | Catalog uses approved bilingual text and rejects unstructured input. Retained exact approval covers four safe replies. Supported for approved catalog. |
| T48.3 | FaqActivity provides resource-backed FAQ and retained HMD checklist observed localized headings and scoped audio. Supported. |
| T48 Done when 1 | SupportTicketControllerTest denies catalog access to USER and ADMIN, and Espresso asserts USER FAQ and question are visible. Supported. |
| T49.1 | Diagnostic repository assertions require exactly bankCode, formatVersion and errorCode and exercise unknown metadata. Retained cancel/confirm evidence supports report submission. |
| T49.2 | SmsDiagnosticControllerTest asserts aggregation/dedup and one flag plus ADMIN-only advancement. Supports Sahan's STAFF integration. |
| T49.3 | Format editing is ADMIN scoped. Retained format controller role-denial and app role flows support the requirement. |
| T49 Done when 1 | Exact serialized field set excludes raw SMS, amounts and account numbers. API assertions reject private fields and retained format role tests cover forbidden STAFF edits. Supported. |
| T51.1 | Espresso journey verifies registration, authenticated create, dashboard expense, budget remaining, edited amount, empty list after delete and denied SMS. Supported by assertions and retained execution. |
| T51.2 | Retained API 26 emulator connected run and isolated Android 14 handset run each completed four tests. Supported. |
| T51.3 | MD05 checkpoint preserves sanitized XML/output references and secure-capture control results. Supported by retained artifacts and summaries. |
| T51 Done when 1 | Retained API 26 connected output and HMD direct instrumentation output are distinguished in the checkpoint. Supported. |

**Coverage after this batch.** All 84 original MD criteria now have an individual disposition in this checkpoint. This includes partial and open dispositions and does not certify 84 passes. Total register coverage is 91 of 220 rows. Remaining IDs are the register rows outside those 84 original MD rows and the seven T07 rows. No baseline tests were repeated. Review of the cross-team rows and eventual tracker reconciliation are pending.

## Component audit for accounts, categories and transaction records

| Criterion | Evidence and disposition |
| --- | --- |
| T09.1 | Sevindu API dependency. Owner-scoped account lookup and archived-instead-of-deleted branch. Retained tests assert CRUD, another owner's 404 and one preserved transaction after archival. |
| T09.2 | Sevindu API dependency. Category tests verify defaults and owned-only list, CRUD of owned rows, read-only defaults and cross-owner rejection. Supported. |
| T09.3 | Sevindu API dependency. Category assertions expect 409 for duplicate names, including conflicting edits, and 409 with IN_USE on deletion of referenced categories. Supported. |
| T09.4 | Sahan Android scope. Account list/form paths and retained HMD create/edit/delete and archive checks support the criterion. |
| T09.5 | Sahan Android scope. CategoryListActivity separates income and expense rows, and the retained HMD run exercised editing owned categories and in-use feedback. Supported. |
| T09 Done when 1 | API integration dependency. Retained AccountControllerTest and CategoryControllerTest assertions exercise CRUD, 409, 404 and forbidden default changes. |
| T09 Done when 2 | Sahan device scope. Retained HMD test at 9a8967e covers both screens against the local API. Supported. |
| T10.1 | Sevindu API dependency. Controller tests assert CRUD, optional category, kind validation, archived-account rejection and cross-owner account/category handling. Supported. |
| T10.2 | Sevindu API dependency. Test asserts each filter, combined filters, newest-first dates and exact result count/date for page two. Supported. |
| T10.3 | Sevindu API dependency. Test compares decimal income, expense and net across fixture mutations and excluded dates. Supported. |
| T10.4 | Sahan Android scope. TransactionActivity applies account/category/type/date filters, offers paging and confirmed deletion, and reloads the current page after a successful shared-form edit. Retained HMD evidence covers these routes. |
| T10 Done when 1 | Retained test checks exact 15000.63 expense before edit/delete and 14000.20 afterwards, plus owner filtering and 404. Supported integration dependency. |

**Coverage after this batch.** 103 of 220 register rows have dispositions. The 117 unreviewed IDs are listed below. Examined rows with partial or open dispositions retain their open work. No tracker counts or statuses changed in this pass.
**Unreviewed criterion IDs by task**
- T01.1, T01.2, T01.3, T01.4, T01 Done when 1
- T12.1, T12.2, T12.3, T12.4, T12.5, T12 Done when 1
- T17.1, T17.2, T17.3, T17 Done when 1
- T18.1, T18.2, T18.3, T18.4, T18 Done when 1
- T19.1, T19.2, T19.3, T19 Done when 1
- T21.1, T21.2, T21.3, T21 Done when 1
- T23.1, T23.2, T23.3, T23.4, T23.5, T23.6, T23 Done when 1
- T31.1, T31.2, T31.3, T31 Done when 1
- T32.1, T32.2, T32 Done when 1
- T33.1, T33.2, T33.3, T33.4, T33 Done when 1
- T34.1, T34.2, T34.3, T34.4, T34.5, T34 Done when 1
- T35.1, T35.2, T35.3, T35 Done when 1
- T36.1, T36.2, T36.3, T36.4, T36 Done when 1
- T41.1, T41.2, T41.3, T41 Done when 1
- T42.1, T42.2, T42.3, T42 Done when 1
- T43.1, T43.2, T43.3, T43 Done when 1
- T44.1, T44.2, T44.3, T44 Done when 1
- T45.1, T45.2, T45.3, T45.4, T45.5, T45 Done when 1
- T46.1, T46.2, T46 Done when 1
- T47.1, T47.2, T47.3, T47.4, T47 Done when 1
- T52.1, T52.2, T52.3, T52 Done when 1
- T53.1, T53.2, T53.3, T53 Done when 1
- T55.1, T55.2, T55.3, T55.4, T55 Done when 1
- T56.1, T56.2, T56.3, T56.4, T56.5, T56.6, T56 Done when 1
- T57.1, T57.2, T57.3, T57.4, T57 Done when 1

## CSV, planning, history and data rights

| Criterion | Applicability and disposition |
| --- | --- |
| T12.1 | Sevindu contract consumed by Android. The tracked CSV template specifies column order and ISO dates. Supported. |
| T12.2 | Sevindu preview dependency. CsvImportControllerTest asserts row details, errors, counts and zero persisted records before confirm. Supported. |
| T12.3 | Sevindu API with scoped D39 delegation. Test asserts three confirmed rows, exact stored amount and a 64-character digest. Reimport assertions establish deduplication. |
| T12.4 | Sevindu validation dependency. Tests assert the oversize 413, 2000-row boundary, formula-like cell rejection and zero writes after invalid confirm. Supported by retained suite. |
| T12.5 | Sahan Android. OpenDocument feeds preview. Confirm binds the exact reviewed preview and selected lines. Retained HMD evidence covers pick, confirm, invalid rows and lost-response recovery. |
| T12 Done when 1 | Retained API assertions cover valid input, malformed dates and amounts, oversized files, formula-like cells and reimport flags. Supported. |
| T17.1 | Sevindu API dependency. BudgetControllerTest covers CRUD, duplicate month/category conflict and other-owner 404. |
| T17.2 | Sevindu API dependency. Retained assertions cover used, remaining and status at the warning and over-limit boundaries. |
| T17.3 | Sahan Android. BudgetListActivity renders progress and the retained functional journey creates a budget and checks remaining amount. Targeted physical device evidence and T10 closure remain open. |
| T17 Done when 1 | Retained monthly/category, owner isolation, leap February and threshold tests support the backend assertion. |
| T18.1 | Sevindu API dependency. BillControllerTest covers CRUD, invalid inputs and owner 404. |
| T18.2 | Sevindu API dependency. Retained assertions cover due day 31 clamping, month-specific paid state, repeated payment conflict and unpaid reversal. |
| T18.3 | Sevindu planning dependency. BillControllerTest exercises monthly totals and summary. T31 integration remains open. |
| T18.4 | Sahan Android. BillListActivity parses and displays due date and paid status, with retained mutation refresh evidence. Targeted physical evidence and T17 closure remain open. |
| T18 Done when 1 | Retained tests cover short February, month rollover and duplicate payment conflict. Supported backend dependency. |
| T19.1 | Sevindu API dependency. GoalControllerTest covers CRUD, target validation and another owner's 404. |
| T19.2 | Sevindu API dependency. Retained assertions cover exact progress after add/remove and a display cap of 100 with DONE state. |
| T19.3 | Sahan Android. GoalListActivity opens GoalDetailActivity with contribution history, add and delete. Retained emulator flow supports basic use. Targeted physical evidence and T10 closure remain open. |
| T19 Done when 1 | Partial owner dependency. Tests demonstrate invalid targets, add/delete recomputation and no double counting. The literal edit-contribution case is unsupported because GoalController exposes only list/add/delete for contributions. Sevindu to authorize contribution edit semantics or record an explicit correction to acceptance. Keep this distinct from Android T19.3. |
| T31.1 | Thilina owns financial assumptions and API. PR #14 is a candidate outside this baseline. Open dependency. |
| T31.2 | Thilina owns warning and assumptions contract. Candidate exists, but approved semantics and integration are open. |
| T31.3 | Sahan Android. Keep open until the API contract and prerequisite closure or documented early start. |
| T31 Done when 1 | Thilina owns formula assertions across month boundary and absent income. Candidate verification is separate from baseline acceptance. |
| T32.1 | Thilina owns the read-only simulator contract. PR #13 is a candidate outside baseline. Open dependency. |
| T32.2 | Sahan Android. Simulation screen depends on approved contract and T19 closure or scoped early start. Open. |
| T32 Done when 1 | Thilina's rounding and impossible-goal assertions belong to candidate verification. Keep open on baseline. |
| T33.1 | D41 delegated backend. HistoryControllerTest repeats scheduled snapshots and asserts one row per month. Production activation remains separate. |
| T33.2 | D41 delegated backend. Tests cover pending state, scored past-month edit, date movement and no change on rollback. D55 downstream values remain affected by consumer adoption. |
| T33.3 | D41 delegated API. Tests cover month range, roles, owned rows and null score with retained facts. |
| T33.4 | Sahan Android. HistoryActivity renders the score trend and accessible month list. Retained emulator route and fixture evidence support exercised history. |
| T33 Done when 1 | Retained history assertions cover repeat scheduling and scored past-month edits. D55 downstream integrated acceptance remains open. |
| T34.1 | Sevindu device-registration API dependency. No implemented path established on baseline. Open. |
| T34.2 | Sevindu event-job dependency. Open with planning prerequisites. |
| T34.3 | Sevindu push delivery and lock-screen contract. Open pending approved FCM infrastructure and contract. |
| T34.4 | Sevindu API opt-out contract. Open prerequisite for Android settings. |
| T34.5 | Sahan Android. No FCM/channel/permission implementation found from the source search. Open pending API and planning prerequisites. |
| T34 Done when 1 | Shared acceptance. Job dedup and opt-outs are Sevindu evidence. Android permission refusal is Sahan evidence. Both remain open. |
| T35.1 | D53 delegated backend. DataRightsControllerTest asserts 18 exported families and exact calendar dates, with foreign rows and credential data excluded. |
| T35.2 | D53 delegated backend. Retained response asserts no-store and the download filename prefix. Exact dated filename and server temp-file path need direct source confirmation. |
| T35.3 | Sahan Settings invokes CreateDocument for JSON. Retained HMD verified the normal export picker, with restricted export supported by retained backend tests. |
| T35 Done when 1 | Retained export assertions cover own/foreign families, 401 without login and no fixture markers in captured output. |
| T36.1 | D53 delegated backend. Retained tests verify password confirmation, account and token removal and preservation of another owner's data. |
| T36.2 | D53 delegated backend. Test verifies USER_DELETED audit row has null actor, target and reason. |
| T36.3 | Sevindu decision dependency. D21 sets encrypted backups and 30-day retention. Implementation and real restore remain T56. |
| T36.4 | Sahan Settings connects confirmation, password and deletion state to session teardown. Retained HMD verifies normal path. |
| T36 Done when 1 | Retained test checks removed rows, old access/refresh rejection and same-email registration. Full cascade constraints also rely on retained schema verification. |

T35.2 source follow-up: ExportController constructs the dated Colombo filename and returns a service Snapshot for JSON serialization. ExportService assembles the snapshot in memory. No server temp-file path appears in this flow. Its filename uses a .json extension as documented.

## Admin controls and staff support

T41–T47 retain Sevindu ownership. The D45/D49/D52/D53 decisions delegate specific backend and Android work to Sahan. The dispositions below use the retained baseline suite and recorded device evidence.

| Criterion | Evidence and disposition |
| --- | --- |
| T41.1 | FirstAdminBootstrapTest checks repeat startup and invalid configuration before DB access. Supports delegated bootstrap. |
| T41.2 | AdminStatsControllerTest asserts exact non-financial counts in a strict JSON comparison. Supported. |
| T41.3 | Retained Android 17 confirms ADMIN Home links for user, staff, category and SMS operations. Supported for exercised paths. |
| T41 Done when 1 | Existing assertions exercise missing/invalid/expired credentials, wrong-role refusal, and strict response keys. Supported. |
| T42.1 | AdminDependencyControllerTest exercises exact user projection and search. Supported. |
| T42.2 | Same test verifies audit reason and credential invalidation after disabling and re-enabling. Supported. |
| T42.3 | Retained Android 17 ADMIN flow covers user search, reason and both status changes. Supported. |
| T42 Done when 1 | Retained tests assert disabled login, old credentials stay invalid, and ADMIN finance-route refusal for the exercised APIs. |
| T43.1 | StaffProvisioningControllerTest verifies no-store provisioning, temporary credential unusable for ordinary login, one successful activation and one rejected repeat. Supported. |
| T43.2 | Test verifies disabled STAFF rejection, old access/refresh invalidation after re-enable and ordinary profile update denial when attempting role assignment. Supported under scoped APIs. |
| T43.3 | Retained Android 17 STAFF provisioning and disable flow supports the ADMIN screen integration. |
| T43 Done when 1 | Retained tests assert STAFF provisioning denial, role-change denial and disabled STAFF 403. Supported. |
| T44.1 | AdminDependencyControllerTest exercises bilingual default management and deactivation of in-use default. D55 test preserves both semantic rows and references across renames. Supported. |
| T44.2 | Retained tests assert duplicate-name denial and reserved semantic codes cannot be claimed by ordinary defaults. USER-owned updates remain in USER category paths. |
| T44.3 | Retained Android ADMIN link and default category management flow support the screen. |
| T44 Done when 1 | CategoryControllerTest asserts USER cannot edit defaults. Admin dependency assertions preserve existing transaction/budget references. D55 downstream consumption remains separate. |
| T45.1 | Draft edit and immutable publication tests plus competing-activation test support versioning and single active approved revision. Scope limited to D35 NSB definitions and D54 validation. |
| T45.2 | Tests bind evidence to exact definition and exercise invalidation and digest approval separately. Validation gates publication and activation. Supported for approved synthetic fixtures. |
| T45.3 | SmsFormatControllerTest checks USER auth, tamper rejection, the approved active snapshot and authoritative empty when disabled. Supported scoped D36 dependency. |
| T45.4 | Admin controller test activates an older immutable revision and checks USER snapshot restoration. Supported. |
| T45.5 | Retained Android 17 ADMIN journey covers editing, testing, publishing, activating and rolling back. Independent operational approval remains separate. |
| T45 Done when 1 | Retained invalid format, rollback and D35 parser fixture tests support the scoped requirement. |
| T46.1 | StaffStatsControllerTest asserts unresolved owned and unassigned counts. Supported delegated dependency. |
| T46.2 | Retained isolated STAFF dashboard observes these counts and sign-out. Supported. |
| T46 Done when 1 | StaffStatsControllerTest verifies disabled/wrong-role denial. Retained EndpointAuthTest covers ADMIN and USER-finance paths. |
| T47.1 | Retained SupportTicketControllerTest asserts canonical ticket creation, optional allowlisted error code and owner-list isolation. |
| T47.2 | Retained tests cover claim concurrency, assignee visibility and lifecycle. HMD exercised claim and reply. |
| T47.3 | Retained tests assert labeled bounded identifiers stripped before storage/response and absent from logs, arbitrary text rejected. No attachment contract in V1. |
| T47.4 | Retained HMD Help and STAFF ticket screens support localized submission, claim and reply flow. |
| T47 Done when 1 | Retained owner/assignee isolation and persisted canonical-body assertions support the privacy clause. |

## Foundational decisions, analytics dependencies, QA and delivery

| Criterion | Applicability and disposition |
| --- | --- |
| T01.1 | Shared dependency and outside Sahan's individual delivery. D01 records lecturer approval for three roles. Primary approval artifact unavailable in checked-out sources. |
| T01.2 | Shared decisions with Sahan participation. D07–D13 record the agreed outcomes. Supported documentation. |
| T01.3 | Shared decision, with scoring implementation owned by Thilina. D10 fixes thresholds and D20 records approved weights/formulas. Supported documentation. |
| T01.4 | Shared environment handoff. Retained suite/build evidence supports a working local environment for this audit. Other members' assertions remain their recorded confirmations. |
| T01 Done when 1 | Decisions D07–D13 exist. The historical parenthetical about P05 is stale because D20 records its later approval. No closure change inferred. |
| T21.1 | Thilina dependency feeding Sahan's Insights/history/advice. Metrics tests assert hand-calculated BigDecimal values. Savings classification remains name-based after D55, so acceptance is partial. |
| T21.2 | Thilina dependency. MetricsServiceTest checks amount, date bounds and row count on the same fixture. Supported. |
| T21.3 | Thilina dependency. Tests check null no-income ratios, D18 refund convention, unclassified category and overlapping payday windows. Supported within the approved model. |
| T21 Done when 1 | Hand-calculated fixture test exists and retained results cover it. The fixtures do not establish D55 rename invariance. |
| T23.1 | D20 formulas are Thilina-owned. Retained score fixture outputs support normal integration. D55 loan classification remains open. |
| T23.2 | Thilina calculator dependency. Retained fixture tests cover decimal score normalization, unavailable total and known score bounds. |
| T23.3 | Thilina DTO contract consumed on Android. Sahan's Insights renders reason keys and facts, including explicit missing and not-applicable states. Supported on tested fixture data. |
| T23.4 | Thilina controller dependency. Retained controller checks cover USER role, owner binding and month validation. |
| T23.5 | Thilina score-input contract consumed by Sahan. Retained Android/DB evidence supports normal declaration flow. Loan-credit contradiction checks still depend on the mutable loan display name. Partial. |
| T23.6 | Outside Sahan's independent implementation authority. Thilina owner review and T18/T19 prerequisites remain open. |
| T23 Done when 1 | Retained calculator tests support the requested baseline cases. D55 rename regression remains an additional integrated gap. |
| T52.1 | Thilina QA matrix, with Sahan focused MD05 evidence. EndpointAuthTest and targeted role tests cover a subset. Full role-by-endpoint matrix remains open. |
| T52.2 | Thilina QA. Existing owner-isolation assertions cover exercised records/support/data rights. All-ID coverage remains open. |
| T52.3 | Thilina QA with Sahan privacy contribution. Strict stats projections and retained role-denial tests support exercised responses. Comprehensive response audit remains open. |
| T52 Done when 1 | Baseline CI success covers checked-in tests. It does not establish a completed matrix. Keep open. |
| T53.1 | Shared dependency. Retained synthetic token expiry and no-marker logs plus password hashing source support partial evidence. Deployed TLS remains T56-dependent. |
| T53.2 | Sahan's retained isolated SMS batch captures and Android exact diagnostic DTO checks support app-side evidence. Thilina owns controlled live-provider evidence. Keep combined criterion open. |
| T53.3 | Thilina QA ownership, outside Sahan's implementation except remediation in assigned code when findings exist. The checklist records comprehensive dependency and secret scanners outstanding. |
| T53 Done when 1 | Security checklist exists with scoped evidence and explicitly open deployed and scanner checks. Keep open. |
| T55.1 | Sahan's T16 synthetic NSB accuracy evidence is 12/12. Supported within D35/D38 scope. |
| T55.2 | Shared QA. Sahan's retained HMD dashboard timing covers one warm transition. All defined main screens and the agreed workload remain open. |
| T55.3 | Shared QA, with Thilina leading evaluation. Requires trial users and real SUS responses. Open. |
| T55.4 | Thilina evaluation ownership. D03 delivers V1 only. Original beta comparison needs owner-approved reconciliation. Open. |
| T55 Done when 1 | Thilina evaluation report remains open. Retained component timing/accuracy are partial inputs only. |
| T56.1 | Sevindu infrastructure outside Sahan's implementation. Setup and service files present. Deployed access and firewall remain unverified. |
| T56.2 | Sevindu infrastructure outside Sahan's implementation. MySQL config and setup present. Deployed bindings and least privilege remain unverified. |
| T56.3 | Sevindu HTTPS hosting dependency for Sahan's release verification. Nginx and certificate scripts exist. D56 environment remains unavailable. |
| T56.4 | Sevindu operations outside Sahan's implementation. Backup/restore scripts and timer exist. A live restore drill remains open. |
| T56.5 | Sahan verifies actual authenticated V1 release. Signing configuration and unsigned artifact evidence exist. Trusted endpoint and signed release verification remain open. D03 supersedes beta delivery scope, and legacy tracker text needs owner reconciliation. |
| T56.6 | Sevindu operations evidence. Deploy runbook and release/rollback scripts cover the documentation clause. No deployed rollback was inferred. |
| T56 Done when 1 | Sahan's phone HTTPS and Sevindu's restore drill are independently open. |
| T57.1 | Shared final acceptance. Retained component runs cover registration through advice. Daily guide and simulator are still open and no complete journey is established. |
| T57.2 | Shared final acceptance. Delegated ADMIN and STAFF device evidence exists by component. Complete final combined run remains open. |
| T57.3 | V1 has one advice experience. Legacy beta clause differs from approved D03. Sevindu as final acceptance lead must reconcile wording and evidence. |
| T57.4 | Shared team delivery. This register documents open defects and evidence. Final handoff requires reconciliation and final revision review. |
| T57 Done when 1 | Shared final acceptance artifact remains open pending integrated results and approved evidence. |

**Current coverage.** 220 of 220 rows have individual dispositions. Remaining IDs: none. Partial and open rows still require closure evidence or owner decisions.

## Tracker reconciliation in this pass

- Reopened T19 Done when 1 because its original edit/delete wording includes an edit operation absent from the contribution routes and retained assertions. T19 remains in progress. The audited baseline register preserves its previous checked state.
- Updated the stale T01 P05 note to reflect team approval as D20. T01 remains in review.
- None of the original MD checkbox counts were promoted. Profile race, D55, planned device work and release checks retain their open dispositions. The register covers all rows, and closure verification remains incomplete.

## Post-audit reconciliation and targeted evidence

| Scope | Evidence-supported acceptance | Open criteria |
| --- | ---: | --- |
| MD01 | 19/23 | T39.2, T39.3, T39.4, T39 Done when 1 |
| MD02 | 13/16 | T13.3, T13.4, T13 Done when 1 |
| MD03 | 9/9 | Formal prerequisites and independent review |
| MD04 | 20/20 | Formal prerequisites and independent review, with D55 and live provider in their own scopes |
| MD05 | 15/16 | T40.1 under D51/D56/T56 |
| Original delegated MD05 | 22/22 | T41 4/4, T43 4/4, T45 6/6, T46 3/3 and T47 5/5 within approved scope. Formal review remains. |
| T07 | 6/7 | T07.5 profile-write correction |

- T07, T13 and T39 are in progress in INDEX. Reopened MD criteria retain their original wording. T13.1 verifies the scoped sideload note and packaging control, with the original proposal still unavailable. T13.3, T13.4 and the device criterion retain prior evidence, but require a current integrated permission and fallback run. D34 permits emulator settings revocation/regrant evidence only for its specified part.
- All 37 original T39 pairs in the resources at revision 92f2dd3 match the current XML in both locales. This confirms the original scope alongside the four pinned approval inventories already checked. Native-speaker coverage of the remaining applicable strings and a bilingual full-screen device walk remain open.
- Android 17, read-only FINORA_D54 emulator, existing V1 baseline debug and AndroidTest APKs. At font scale 1.30, direct instrumentation for registrationRequiresBothConsentCheckboxesInEachLanguage reported OK (1 test) in 6.458 seconds. It asserts visible registration controls and consent copy, and the submit gate in EN and SI. No backend required. The emulator was stopped. The result does not measure unclipped glyphs or spoken TalkBack.
- Remediation closed tracker evidence discrepancies. No application implementation change was made. The known profile-write race requires Sevindu's approval of the recorded API correction. T19 contribution editing also remains with Sevindu. Thilina owns T21/T23 D55 adoption and downstream metrics, score, history and advice verification.
- Technical acceptance remains partial. Complete current SMS fallback and settings workflows with a disposable API/emulator, then map remaining Sahan UI strings to approvals and finish the scoped device evidence. Formal acceptance requires independent review and outstanding dependencies. Integration still requires the team contracts and tracker overlaps to be reconciled. Deployment still requires T56 HTTPS and restore evidence. No handset was requested for the blocked T17.3/T18.4/T19.3 acceptance.

## Focused SMS and large-text verification

- Built and ran new instrumentation against a read-only Android 17 FINORA_D54 emulator and disposable loopback MySQL-backed API. The fallback test asserts explanation visibility, opens Paste SMS, CSV Import and Manual entry while READ_SMS is denied, checks return to the SMS screen after each, and uses No thanks without granting access. Result: OK (1 test), 15.972 seconds. The first harness run raced activity transition. Awaiting the resumed activity corrected it.
- The Android runtime dialog denial/grant test checks denied state, hidden Scan, then actual grant and visible Scan. Result: OK (1 test), 14.094 seconds on the initial emulator run. Permanent denial with two refusals, App Info, Permissions, SMS, Allow and return to FINORA passed in 26.464 seconds. D34 retained HMD App Info and prior Settings revocation evidence remain applicable. Restore T13.3 and T13.4. The separate current physical permission outcome criterion stays open.
- The isolated bilingual FAQ layout probe at 130% passed in 15.895 seconds. It switched locale using the live disposable profile API and checked nine labels for heading semantics, line width, height and no ellipses in EN and SI. This covers FAQ only. The earlier assumed skip and premature runs after permission resets are excluded from evidence.
- The full register remains 220/220 dispositions. Current MD evidence is 19/23, 15/16, 9/9, 20/20 and 15/16, totaling 78/84. Original delegated scope is 22/22. T07 stays 6/7. No authorization for the owner-controlled profile API change was found in updated repo decisions. Sevindu retains the proposed race correction and T19 editing decision. Thilina retains D55 downstream adoption.
- Prepared the D50 application and test APKs with the separate acceptance package IDs. Consolidated HMD checklist still needs its complete fixture setup. It must include a current physical explanation/refusal/grant/permanent-denial sequence, fallback navigation, both-language walkthrough at normal and larger type, screening for clipped text, and genuine spoken TalkBack labels and Back focus. Use retained physical findings for unaffected cases. Check original profile, permission, font, locale, accessibility service and reverse mapping before and after. Exclude budget, bill and goal physical acceptance until their prerequisites close.
- D50 package pair also passed the opt-in runtime denial and grant test on the disposable emulator (OK (1 test), 14.584 seconds). No physical Samsung/HMD outcome is inferred from this package rehearsal. The temporary emulator route, emulator and disposable API/database were stopped.


## D50 handset preparation and remaining localization evidence

- On the clean `0450677` baseline, verified the D50 acceptance and runner package IDs and matching signer. A fresh disposable MySQL schema backed the loopback API. Health was UP. A synthetic registration, login, cash account and custom expense category succeeded. No fixture credentials or tokens are retained in this record.
- On the isolated Android 17 emulator, the existing D50 `registerLoginAndManageFinancialRecordsWithSmsDenied` method reported `OK (1 test)` in 28.281 seconds. It exercises in-process signup, FAQ, account creation, exact decimal transaction entry and dashboard totals, emulator-only budget setup, transaction edit/delete and the SMS refusal route. It does not close the current handset criterion.
- [Pending native-speaker inventory](t39-pending-translation-review.md): 460 matching resource keys, 371 unchanged approved pairs and 89 pending. Includes 28 registration, Settings and shared records labels and 61 Sevindu planning labels. The exact revised category removal labels remain pending. T39.4 stays open. The profile-write defect and the required owner authorization remain unchanged.

### Consolidated HMD checklist

1. Verify serial, current and original profile, D50 package identity/signing and absence of a pre-existing conflicting reverse mapping. Snapshot test-profile font, locale, TalkBack, permission flags and active user. Use the loopback API and the guarded test runner for synthetic registration and sign-in. Use separate isolated USER sessions for the walkthrough and permission transitions.
2. Visit login, registration, the USER tabs, account/category and transaction list/forms, CSV, Paste SMS, SMS access, Settings, Insights, History, Advice, FAQ and Support in English and Sinhala at normal and 130% text. Cover ADMIN and STAFF routes only with approved disposable fixtures. Record failed or unvisited screens explicitly. Keep planning device acceptance gated; the emulator budget path is fixture rehearsal.
3. Automate navigation, activity visibility, text fit and accessibility focus checks where reliable. For a single TalkBack listening pass, confirm main-control announcements, headings, selection/error feedback and focus after Back in both languages. Reuse prior FAQ/Support and consent evidence where applicable. Do not infer audible output from node text.
4. Exercise the physical SMS explanation and No thanks, ordinary denial with all fallbacks working, permanent denial with App Info guidance, and actual grant with Scan available. Use an isolated permission sequence and retain the HMD Settings limitation if it recurs. D34 combined validation remains scoped to T13.4. Do not open the inbox or run a scan on the phone.
5. Verify restoration of the test-profile permission, locale, TalkBack and font settings, remove the temporary reverse route, sign out the disposable session and return to the original profile. Report any restoration gap before advising disconnection.


### HMD Crest targeted results

- Installed the matching D50 pair only in existing isolated user 10 of the authorized Android 14 HMD Crest. The owner profile did not have the acceptance package installed. Guarded synthetic session staging reported `OK (1 test)` in 4.268 seconds.
- Observed the Sinhala SMS explanation and No thanks, the real first denial and rationale, the second denial and Settings guidance, and the App info screen. Tapping Permissions left App info visible in the isolated profile. All three fallback activities opened under persistent denial. A fresh physical grant was not verified; the current physical outcome checkbox remains open. No inbox scan was performed. Emulator evidence remains separately scoped under D34.
- At 130% text, captured the Sinhala Records labels and bounds and enabled TalkBack once. No new listener result was received during the session. The full bilingual screen, text-fit and spoken verification criteria remain open.
- Signed out to the acceptance login. Read back font scale 1.0, accessibility disabled with no enabled services, and READ_SMS denied with original OS flags. Removed the temporary reverse route and UI dump, then confirmed the original Android user 0. The acceptance app retains its request history; no app storage was cleared and no personal app was changed.


## T39 localization approval and final D50 permission recovery

- Sahan approved 28 exact registration, Settings and shared Records pairs against `4a1cb52`, with ten Sinhala corrections applied in `e2216b6`. The 18 remaining pairs and all 371 earlier approved pairs are unchanged. The 61 planning pairs remain separately pending. XML, 460-key resource parity, format arguments, plurals and the four literal consent newline escapes passed. D50 build and lint passed, with no lint issues. See the [pinned inventory](t39-pending-translation-review.md).
- On the existing Android 14 HMD Crest test profile, the rebuilt D50 APK showed Android's actual permission dialog after temporarily resetting the approved app and OS prompt histories. Selecting Allow in the dialog made FINORA show granted status and Scan. No SMS scan or ADB grant occurred. Together with the current HMD ordinary/permanent denial and fallback evidence above, this closes the T13 handset criterion. T13 enters review.
- At 130% text Sahan confirmed audible Records, Add transaction, CSV import, SMS access, Language and Scan in English and Sinhala. Sahan also reported SMS access as the first spoken label after Back in both languages. The initial Add transaction attempt spoke the Android test-profile setup warning. A longer probe pause separated speech from runner teardown, and Sahan confirmed the retry.
- Exact accessibility focus after Back did not hold on the SMS access button in the previous build. The HMD regression reproduced the failure. MainActivity now uses its existing focus-return route for SMS access. The patched HMD node-focus regression passed in Sinhala (8.306 seconds) and English (8.333 seconds). The speech evidence and exact focus assertions are separate. This does not complete the full every-screen bilingual walkthrough or T39.3.
- With the corrected resources and the patched D50 application, the HMD registration control and consent-text visibility test passed in both languages at 130% (2.429 seconds) and 100% (2.566 seconds). It exercises visibility and the consent gate. Full visual glyph/text-fit inspection remains open outside the observed coverage.
- Signed out of the disposable session. Restored and verified SMS denied with original OS flags, the original FINORA prompt-history value, font scale 1.0, no enabled accessibility service, accessibility off and original Android user 0. Returned the acceptance locale to Sinhala, removed the temporary reverse route and UI dump, and left the updated D50 APKs in the test profile. The personal application and its data were outside the test.
- Acceptance is MD01 19/23, MD02 16/16, MD03 9/9, MD04 20/20 and MD05 15/16, totaling 79/84. Original delegated scope stays 22/22 and T07 stays 6/7. T39.2/T07.5 still require Sevindu's authorization to fix the profile race. T39.4 stays open for the remaining planning pairs. Owner prerequisites, D55, D51/D56/T56 and independent reviews remain open.


## T39 targeted bilingual emulator closure

- Ran the D50 app against the disposable synthetic loopback API on read-only Android 17. Genuine UI routes exercised USER, ADMIN and STAFF at 100% and 130% in EN and SI. USER coverage includes all five main tabs, accounts/categories and forms, transaction list and manual form, CSV, SMS access and Paste SMS, budget/bill/goal lists and forms, a synthetic goal detail, settings, insights, advice, monthly history and support. Role coverage includes ADMIN/STAFF homes, ADMIN user/default-category/staff management, both diagnostic routes, ADMIN SMS sample checker and STAFF support. Login and registration were checked in both languages. The FAQ reused the retained 130% bilingual probe and passed its new 100% check. The matrix covers entry states for all 31 manifest activities, with separate checks for the main tabs and roles that share an activity.
- Tests assert the resumed activity's language and font scale, that it has laid-out text, and no measured width overflow, height overflow or ellipses in the sampled non-editable TextViews. The localized navigation labels were exercised through the UI. These assertions do not verify editable hints, every populated/error/dialog state, custom-drawn chart strings, rendering on other hardware, or actual speech. Retained HMD Sinhala samples establish device script rendering. Together the evidence supports T39.3. No production defect was reproduced by this pass and no production source changed.
- Exact instrumentation results: USER 130% 49.897 s and 100% 54.878 s, role routes 130% 19.754 s and 100% 21.972 s, registration 130% 5.192 s and 100% 5.848 s, FAQ 100% 10.038 s. Each reported OK (1 test). The earlier retained FAQ 130% pass was not rerun. D50 build and lint passed with no issues. Reproduce using the opt-in methods in `UserJourneyTest` and the disposable ADMIN/STAFF fixtures.
- The isolated ADMIN listener probe reported OK (1 test) in 48.721 seconds. It moved and verified accessibility focus on the four ADMIN main actions in each language. Sahan separately confirmed all four audible and correct in both languages through the PC speakers. Together with the retained HMD speech samples and the 31-activity bilingual walkthrough, this supports T39 Done when 1. The initial emulator run was interrupted by an Android permission prompt and did not count.
- No handset change was made. Re-audit T39.3 using the exact fit probes and retained handset samples. Leave T40.1 open for Sevindu's trusted T56 endpoint and subsequent release verification, and preserve Sevindu's T19 contribution semantics and restore-drill ownership, Thilina's D55/analytics gates, formal prerequisites and independent reviews.

- Current totals are MD01 21/23, MD02 16/16, MD03 9/9, MD04 20/20 and MD05 15/16, overall 81/84. Delegated scope stays 22/22 and T07 6/7. T13 stays in review. T39 stays in progress because T39.2 and T07.5 await Sevindu's profile API authorization and T39.4 awaits review of the 61 planning pairs. The other unchecked original MD criterion is T40.1, deferred until trusted deployment HTTPS is available. Emulator font, accessibility settings and ADB route were restored and the read-only AVD and disposable API were stopped.

## Targeted re-audit of 2fa8f14

- T13 physical acceptance stands. Retained Android 14 D50 observations cover real refusal, permanent denial, navigation to all fallbacks, and a separate real grant exposing Scan. D34 remains specific to Settings recovery. The MD02 header now matches its 16 checked rows and T13 INDEX review.
- The SMS Back focus change uses MainActivity's existing focus-return route. The guarded handset probe checks package and test profile, injects Back, and verifies exact restored accessibility focus in each locale. Retained audible results come from Sahan's listening confirmation.
- Exact XML comparison with 4a1cb52 found zero English changes and ten Sinhala changes. All 89 inventory rows match current XML, including the 28 approved and 61 pending pairs. The current Sinhala blob matches the inventory pin. Every other resource pair is unchanged from 4a1cb52, which preserves the 371 previously approved pairs.
- T39.3 retains the physical Sinhala rendering samples and measured long-label fit in the 100%/130% emulator entry states. Coverage is limited to sampled laid-out non-editable TextViews. It excludes editable hints, custom-drawn text, unvisited states, other devices and speech.
- Reopened T39 Done when 1. The 31-activity matrix is emulator entry-state coverage. HMD walkthrough and actual listening observations are scoped to selected controls and screens. The acceptance guide requires explicit approval for alternate environments, and no T39-specific approval of the emulator substitution was found. Retain both evidence sets and complete the remaining bilingual handset routes or obtain and record the applicable approval. No phone requested for this evidence review.
- Superseding counts: MD01 20/23, MD02 16/16, MD03 9/9, MD04 20/20, MD05 15/16, overall 80/84. T07 6/7 and original delegated MD05 22/22. Remaining MD IDs are T39.2, T39.4, T39 Done when 1 and T40.1. No new production defect confirmed in the targeted changes. Formal approval, Sevindu's profile/T19/planning-localization/T56 obligations and Thilina's D55 integration remain open.


## T39 physical-device criterion at the targeted re-audit baseline

- T39 Done when 1 requires every screen in both languages on a physical device and TalkBack reading the main buttons. The newly enabled D50 probes used Android 14 on the authorized HMD Crest test profile (Android user and serial 10), synthetic accounts and a loopback-only API. The original package/profile checks remain in force, with the additional explicit `finoraT39Handset` opt-in. The app and instrumentation packages had matching signers. SHA-256 of the APKs used, app then test: `ff5f61081a5c4063cfafaeb525c6f9c333b71b256dd0605f38d6291c6731aa33` and `915cde0d96eb6943464f318c08740511c1a24c173ea65292ee9c399dc017b515`.
- At 130% text the handset USER route test passed in 52.439 seconds and the ADMIN/STAFF route test passed in 19.245 seconds, each reporting OK (1 test). The probes navigated Login and the five USER tabs, both account/category list-form pairs, transaction list/manual form, CSV, SMS and Paste SMS, budget/bill/goal lists and forms plus a synthetic goal detail, Settings, Insights, Advice, History, Support, both role homes, ADMIN user/category/staff management, STAFF Support, both diagnostics and the ADMIN format sample screen. Both locales passed the sampled rendered-label width, height and ellipsis assertions. Together with the retained corrected registration and bilingual FAQ handset evidence this covers entry routes for all 31 activities. These checks do not certify every loaded/error/dialog state, editable hint or chart glyph. Planning screen text checks do not close T17.3, T18.4 or T19.3.
- Android node-focus checks passed for all four ADMIN buttons in EN and SI (OK (1 test), 50.500 seconds). Sahan separately confirmed all four labels audible and correct in both languages on the physical phone. Retained HMD USER and STAFF speech and verified SMS Back focus remain valid. This is physical evidence, so the criterion does not rely on an emulator exception.
- Targeted verification passed for the changed test runner and criterion. D50 application and runner assembled, lint reported no issues and no production or resource files changed. T13 was not rerun. The test profile was restored to font scale 1.0, accessibility off with no enabled service, and READ_SMS denied with original flags. The temporary reverse route was removed and original Android user 0 verified. The isolated API/database were stopped.
- Restored T39 Done when 1. MD01–MD05 are 21/23, 16/16, 9/9, 20/20 and 15/16, total 81/84. Delegated acceptance is 22/22 and T07 remains 6/7. T39 stays in progress for T39.2 and T39.4. T40.1, Sevindu's other dependencies, Thilina's D55 adoption and the formal review/merge requirements remain open.
