# Sahan 6 — UI modernisation (T58)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decision D61.
**Detail: outline** — T58.0 visual direction must be shown to Sevindu before T58.1 coding.

---

### T58 · Modern Material 3 UI · `UX-01`
**Lead:** Sahan · **Depends on:** T37 (T20/T24/T38 in `review` allowed by D61) · **Touches:** `android/app/build.gradle`, `res:`, `and:ui/`
**Constraints:** Java + XML Views only. Keep every existing view ID, behaviour and API call. Every new string in `values/strings.xml` and `values-si/strings.xml`. `FLAG_SECURE` stays. Use ViewBinding when converting a screen from code-built views to XML.
- [ ] T58.0 Visual direction: colour roles (light/dark), type scale, one Home and one list mockup; Sevindu approves
- [ ] T58.1 Foundation: add `com.google.android.material`; `Theme.Material3.DayNight.NoActionBar` with FINORA colours; edge-to-edge insets; Noto Sans + Noto Sans Sinhala; dark mode
- [ ] T58.2 Shell: `BottomNavigationView` with icons (Home, Records, Plan, Insights, More) replacing the `RadioGroup`; `MaterialToolbar` with Up on every sub-screen; FAB "Add transaction" on Home and Records
- [ ] T58.3 Dashboard: balance + income/expense card, 0–100 score ring, budget progress bars, upcoming bills, recent transactions, focus goal card; restyled charts with text equivalents
- [ ] T58.4 Lists: `MaterialCardView` rows with category icon, amount right-aligned (expense/income colours), overflow menu or swipe-to-delete with Undo instead of two buttons per row; empty/loading/error states
- [ ] T58.5 Forms: outlined `TextInputLayout` with "Rs." prefix, chips for type/category, Material date picker, inline errors
- [ ] T58.6 Remaining screens (Insights, Advice, History, Settings, Help, Support, Admin, Staff) with the same components; convert code-built screens to XML layouts

**Done when**
- [ ] `testV1DebugUnitTest`, `assembleV1Debug`, lint with zero new issues; existing Espresso/UIAutomator suites pass
- [ ] Every screen checked on an emulator in EN, SI, light and dark, at large font; acceptance re-run per `docs/android-acceptance-testing.md`

**Handoff notes:**
- T58.0 proposal: [visual direction](../../docs/t58-visual-direction.md). Verified all 32 light/dark text pairs at or above 4.5:1 and checked both mockups.
- T58.0 remains unchecked until Sevindu approves this proposal. T58.1 is gated on that approval.
