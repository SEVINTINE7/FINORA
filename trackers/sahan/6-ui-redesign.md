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
- [ ] T58.6 Remaining screens (Insights, Advice, History, Settings, Help, Support, Admin, Staff) with the same components; convert code-built screens to XML layouts

**Done when**
- [ ] `testV1DebugUnitTest`, `assembleV1Debug`, lint with zero new issues; existing Espresso/UIAutomator suites pass
- [ ] Every screen checked on an emulator in EN, SI, light and dark, at large font; acceptance re-run per `docs/android-acceptance-testing.md`

**Handoff notes:**
- T58.0 [visual direction](../../docs/t58-visual-direction.md): 32 text contrast pairs passed 4.5:1. Sahan reports Sevindu personally reviewed and verbally approved `11957ae`. Written approval was not independently verified.
- T58.1–T58.3 added Material 1.13.0, secure-window insets, Noto fonts, shared shell and Home cards with exact LKR values and chart text equivalents. Their 152 JVM tests, debug builds, baseline lint, and EN/SI light/dark emulator checks at 130% passed.
- T58.4 added Material list cards, per-row overflow and signed amounts. Four emulator cases verified loading, retry, exact debit, cancellation, confirmed delete, empty state and `FLAG_SECURE` after the Gradle checks passed.
- T58.5 added outlined fields, rupee prefixes, choice chips, inline errors and secure Material date pickers. All existing IDs remain. 152/152 JVM tests passed, V1 Debug and test APKs built, lint had no errors and four baseline warnings, and four isolated emulator cases passed EN/SI × light/dark at 130%. The transaction fixture verified validation, draft recreation, picker `FLAG_SECURE` and exact amount/category/date payload.
- T58.6 follow-up: relocate STAFF activation away from initial sign-in, preserve registration and a discoverable first-time activation path. Source trace confirmed common login routes server-issued USER/ADMIN/STAFF roles. Before removing the initial link, exercise all three common-login routes and separate first-time activation against the isolated backend.
- Pending Sinhala native-speaker review (12 pairs): `shell_navigate_up`, `dashboard_monthly_balance`, `dashboard_upcoming_bills`, `dashboard_recent_transactions`, `dashboard_focus_goal`, `records_actions_named`, `records_actions_collapsed`, `records_actions_expanded`, `transaction_card_details`, `transaction_card_credit`, `transaction_card_debit`, `form_rupees_prefix`. English/Sinhala keys and format placeholders match. Full redesign acceptance remains open.
