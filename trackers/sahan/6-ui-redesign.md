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
- [ ] T58.4 Lists: `MaterialCardView` rows with category icon, amount right-aligned (expense/income colours), overflow menu or swipe-to-delete with Undo instead of two buttons per row; empty/loading/error states
- [ ] T58.5 Forms: outlined `TextInputLayout` with "Rs." prefix, chips for type/category, Material date picker, inline errors
- [ ] T58.6 Remaining screens (Insights, Advice, History, Settings, Help, Support, Admin, Staff) with the same components; convert code-built screens to XML layouts

**Done when**
- [ ] `testV1DebugUnitTest`, `assembleV1Debug`, lint with zero new issues; existing Espresso/UIAutomator suites pass
- [ ] Every screen checked on an emulator in EN, SI, light and dark, at large font; acceptance re-run per `docs/android-acceptance-testing.md`

**Handoff notes:**
- T58.0 [visual direction](../../docs/t58-visual-direction.md): 32 text contrast pairs passed 4.5:1. Sahan reports Sevindu personally reviewed and verbally approved `11957ae`. Written approval was not independently verified.
- T58.1 added Material 1.13.0, light/dark roles, secure-window insets and bundled Noto fonts. All 152 tests, V1 build and lint passed (four baseline warnings). Login smoke passed EN/SI × light/dark at 130%, with the original sign-in ID and `FLAG_SECURE`.
- T58.2 added the five labeled tabs, shared Up via Back, and Home/Records FAB. All 152 tests, V1 and test-APK builds passed, plus four emulator shell runs at 130%. Lint had four baseline warnings.
- T58.3 kept the dashboard totals/trend contract and uses existing USER reads for score, budgets, bills, records and the nearest active goal. Cards show exact LKR values and text equivalents for charts and progress.
- T58.3: 152 JVM tests passed, V1 and test APKs built, and lint had zero errors and four baseline warnings. Four isolated emulator cases with synthetic responses passed EN/SI × light/dark at 130%, covering the cards, month change and `FLAG_SECURE`.
- Native-speaker review is pending for Navigate up and the four new dashboard headings. Full redesign acceptance remains open.
