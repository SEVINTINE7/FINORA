# Sahan 6 — UI modernisation (T58)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decision D61.
**Detail: outline** — T58.0 visual direction must be shown to Sevindu before T58.1 coding.

---

### T58 · Modern Material 3 UI · `UX-01`
**Lead:** Sahan · **Depends on:** T37 (T20/T24/T38 in `review` allowed by D61) · **Touches:** `android/app/build.gradle`, `res:`, `and:ui/`
**Constraints:** Java + XML Views only. Keep every existing view ID, behaviour and API call. Every new string in `values/strings.xml` and `values-si/strings.xml`. `FLAG_SECURE` stays. Use ViewBinding when converting a screen from code-built views to XML.
- [x] T58.0 Visual direction: colour roles (light/dark), type scale, one Home and one list mockup; Sevindu approves
- [x] T58.1 Foundation: add `com.google.android.material`; `Theme.Material3.DayNight.NoActionBar` with FINORA colours; edge-to-edge insets; Noto Sans + Noto Sans Sinhala; dark mode
- [x] T58.2 Shell: `BottomNavigationView` with icons (Home, Records, Plan, Insights, More) replacing the `RadioGroup`; `MaterialToolbar` with Up on every sub-screen; FAB "Add transaction" on Home and Records
- [x] T58.3 Dashboard: balance + income/expense card, 0–100 score ring, budget progress bars, upcoming bills, recent transactions, focus goal card; restyled charts with text equivalents
- [x] T58.4 Lists: `MaterialCardView` rows with category icon, amount right-aligned (expense/income colours), overflow menu or swipe-to-delete with Undo instead of two buttons per row; empty/loading/error states
- [x] T58.5 Forms: outlined `TextInputLayout` with "Rs." prefix, chips for type/category, Material date picker, inline errors
- [x] T58.6 Remaining screens (Insights, Advice, History, Settings, Help, Support, Admin, Staff) with the same components; convert code-built screens to XML layouts

**Done when**
- [ ] `testV1DebugUnitTest`, `assembleV1Debug`, lint with zero new issues; existing Espresso/UIAutomator suites pass
- [ ] Every screen checked on an emulator in EN, SI, light and dark, at large font; acceptance re-run per `docs/android-acceptance-testing.md`

**Handoff notes:**
- T58.0 [visual direction](../../docs/t58-visual-direction.md): 32 contrast pairs passed 4.5:1. Sahan reports Sevindu reviewed and verbally approved `11957ae`. Written approval was not independently verified.
- T58.1–T58.5 Gradle and scoped emulator regressions are recorded in their respective commits. The secure shell, exact monetary amounts and form behavior were retained.
- T58.6 moved the Insights, Advice, History, Help, Support, Admin and Staff screen scaffolds to XML/ViewBinding, reused Material controls, and retained Settings and Login IDs. Login now offers first-time staff activation inside secure Sign-in options and retains normal registration.
- T58.6: 152/152 JVM tests, V1 Debug and test APK builds passed; lint 0 errors and 4 existing warnings. Isolated Android 17 and current Spring Boot/MySQL passed 8 light and 4 dark UI tests, including EN/SI USER/ADMIN/STAFF routes and registration at 130%, first-time staff activation followed by common sign-in, consent re-authentication, and secure login dialogs.
- Direct API checks confirmed backend roles, pending-staff rejection, activation without a session, refresh rotation, logout revocation and 401/403 role guards. Full Espresso/UIAutomator suite, exhaustive all-screen acceptance and independent audit remain open. T58 stays in progress.
- Pending Sinhala native-speaker review (13 pairs): `shell_navigate_up`, `dashboard_monthly_balance`, `dashboard_upcoming_bills`, `dashboard_recent_transactions`, `dashboard_focus_goal`, `records_actions_named`, `records_actions_collapsed`, `records_actions_expanded`, `transaction_card_details`, `transaction_card_credit`, `transaction_card_debit`, `form_rupees_prefix`, `login_sign_in_options`. English/Sinhala keys and format placeholders match. Full redesign acceptance remains open.

- Integrated candidate: bilingual light/dark layout probes at 130% covered the 33 declared activity entry layouts. The focused FAQ and registration rerun passed in both themes after the test switched to the form's Back control. Debug lint passed with four retained warnings. Native Sinhala wording and independent acceptance remain open. [Verification and limitations](../../docs/acceptance.md#integrated-acceptance-follow-up).
