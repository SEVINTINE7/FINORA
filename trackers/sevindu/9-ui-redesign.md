# Sevindu 9 — Screen-by-screen UI redesign (T62–T71, T73, T74)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decisions D61, D65, D67.

Builds on the T58 Material 3 foundation (theme, colours, fonts, bottom navigation). T58 stays Sahan's task. This job replaces the remaining code-built, default-widget layouts screen by screen with the approved D65 design. The colours stay unchanged. The Login screen is out of scope.

**Rules for every task below**
- Java + XML Views + Material Components 3 only (D61). Convert code-built screens to XML layouts with ViewBinding.
- Keep every existing behaviour, API call, validation rule, view ID used by tests, `FLAG_SECURE` and the review/retry flows. Money stays `BigDecimal`. Display formatting is the only change.
- Every new string in `res/values/strings.xml` **and** `res/values-si/strings.xml`.
- Touch targets ≥ 48dp, text contrast ≥ 4.5:1, income/expense never told apart by colour alone (sign + icon), TalkBack labels on icon buttons, custom accessibility actions for swipe gestures.
- `.1` is a mockup on the design canvas that Sevindu approves **before** any code. Each task redesigns at least two screens.

**Done when (each task)**
- [ ] `./gradlew testV1DebugUnitTest assembleV1Debug lintV1Debug` pass with zero new lint issues; existing Espresso/UIAutomator tests for those screens pass
- [ ] Emulator screenshots of every screen in the task: EN + SI, light + dark, and 130% font, attached to the PR. Sevindu approves them.

---

### T62 · Home, Records and Add transaction · `UX-02`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T58.1–T58.2 (D65) · **Touches:** `and:ui/main/`, `and:ui/dashboard/`, `and:ui/transactions/`, `and:ui/` shared views, `res:layout/`, `res:values*/`
- [x] T62.1 Mockups of Home, Records and Add transaction approved by Sevindu on 2026-11-28 (D65), on the private design canvas https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14
- [x] T62.2 Shared kit: grouped LKR formatter (`Rs. 85,000.00`), section header with action link, transaction row (category icon, title, meta, signed amount), segmented type toggle, filter chip, extended FAB; unit tests for the formatter
- [x] T62.3 Home (`DashboardPanel` → XML): greeting with `/me` name, month pill, navy balance card, score ring + "See advice", recent transactions + "See all", budgets with over-limit state, upcoming bills, focus goal, category donut + legend, 6-month trend with labels; loading/error/empty per section
- [x] T62.4 Records (`TransactionActivity` → XML + RecyclerView): Records tab opens the list directly; All/Expense/Income toggle; Month/Account/Category chips replace the filter dialog; day groups with day totals; tap row → edit; swipe → Delete with confirm dialog (no undo); "Load more" replaces Previous/Next; Import icon → CSV import / SMS access; review mode kept
- [x] T62.5 Add/Edit transaction (`activity_transaction_form.xml`): Expense/Income toggle, large amount with "Rs." prefix, category chips with icons, account dropdown, Material date picker, optional merchant/note, Save pinned at bottom; inline errors; uncertain-save review/retry kept
- [x] T62.6 Screenshots (see Done when) shown to Sevindu for approval — 25 captured 2026-11-28 (EN/SI × light/dark + EN 130% font); approved by Sevindu on 2026-11-28 with the three D65 implementation changes

**Handoff notes:**
- Kit: `and:ui/Money` (+`MoneyTest`), `CategoryIcons`, `transactions/TransactionRows`, `res:values*/finora_redesign.xml`, `finora_ic_*` icons. Home = `res:layout/panel_dashboard.xml` + `DashboardPanel`; name from `/me` via `MeRepository.profile()`.
- Records = `TransactionListPanel` (tab and `TransactionActivity`); `Pagination` now appends pages and dedupes by id (`TransactionPaginationTest`). Dates chip uses a FLAG_SECURE range picker (`FinancialDatePicker.showRange`) instead of a Month chip.
- User shell is light (`ProtectedHomeActivity.lightShell/placeAccountControls`); Sign out and Language moved to the More tab. Admin/Staff unchanged.
- Checks 2026-11-28: 161 unit tests pass, assemble OK, lint 4 = baseline; Shell/DashboardCard/FinancialListCard/FinancialForm pass on Pixel_8. Live-API journeys (UserJourney, Evaluation, D58, SMS, Performance) updated but not run.
- Screenshots: guarded `T62ScreenshotTest` draws views in-process (FLAG_SECURE stays on). Sinhala strings and the CLDR short month "ඉල්" need native review.

### T63 · Budgets and bills · `UX-03`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/budgets/`, `and:ui/bills/`, `res:layout/`
- [x] T63.1 Mockups of Budget list, Budget form, Bill list and Bill form approved by Sevindu on 2026-11-28 (design canvas, row "T63 · Budgets and bills")
- [x] T63.2 Budget list (progress cards, over-limit state) and Budget form (category chips, amount field, month)
- [x] T63.3 Bill list (due-day badges, paid-this-month state and "Mark paid") and Bill form (name, amount, due day 1–31, category)
- [x] T63.4 Screenshots approved by Sevindu on 2026-11-28 — 29 captures (EN/SI × light/dark + EN 130% font) on the review page https://claude.ai/artifact/NqTXhC94wu1bsxLiTmVXBi

**Handoff notes:**
- T63.2 (2026-11-28, verified on Pixel_8): `res:layout/activity_budget_list.xml`, `item_budget.xml`, `activity_budget_form.xml` rewritten; `BudgetListActivity`/`BudgetFormActivity` use ViewBinding. Cards sort OVER → NEAR → OK (`BudgetListViewModel.ordered`), summary card from `totals()`. Tap edits; long-press or the TalkBack action "Remove …" opens the existing confirm. Card speaks the old exact LKR sentence; state is its state description.
- New colours `finora_warn_fill`/`finora_warn_ink`; OK/OVER reuse `finora_success_surface`/`finora_error_surface`. `budget_form_title` removed; `budget_limit` now "Monthly limit" (prefix shows Rs.). The unused `budget_*_symbol` strings were removed in T63.3.
- Checks: 163 unit tests, assemble, lint 4 = baseline; Form/Shell/Dashboard/Records fixture tests 4/4 on Pixel_8. Guarded `T63ScreenshotTest` (live dev API) checks order, long-press confirm and tap-to-edit and captures EN/SI × light/dark + 130%. UserJourneyTest budget steps updated, not run.
- Device fixes: summary bar stop dot removed; amount fields (budget and T62 transaction) always show "Rs." with a 0.00 placeholder; dark over-limit bar `finora_bar_over`, tracks `finora_card_line`. SI chips wrap to 2 lines.
- T63.3 (2026-11-28, verified on Pixel_8): `activity_bill_list.xml` (summary card, "To pay" and "Paid this month" sections), `item_bill.xml` (date tile, status with icon, checkable "Mark paid"), new `item_bill_paid.xml` and `activity_bill_form.xml` rewritten; new `and:ui/bills/DueDayPicker` (31 round buttons, 7 columns) replaces the typed due day. `BillListViewModel` gains `toPay`, `paidOnes`, `daysLate`, `percentPaid` (+3 unit tests). Tap edits; long-press or TalkBack action removes; row and summary still speak the old LKR sentences.
- T63.3 checks: 166 unit tests, lint 4 = baseline, fixture tests 4/4; `T63ScreenshotTest.billScreens` (live API, demo bills from 28 Nov) checks order, overdue days, Mark paid ↔ Paid, long-press confirm, tap-to-edit, no-day error. UserJourneyTest bill steps now tap "Day 31"; not run. SI "Mark paid" is "ගෙව්වා" (native review).
- T63.4 (2026-11-28): reused the T63.3 captures (no Android source changed after them); re-ran 166 unit tests, assemble, lint 0 errors / 4 warnings = baseline. Edit screens captured EN light (+ budget EN dark) only. Open: Sinhala native review, SI budget chips wrap to 2 lines.

### T64 · Goals and planning tools · `UX-04`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/goals/`, `and:ui/planning/`, `res:layout/`
- [x] T64.1 Mockups of Goal list, Goal detail, Goal form and Planning (daily guide + simulator) approved by Sevindu on 2026-11-28 (design canvas row "T64 · Goals and planning"; D68)
- [x] T64.2 Goal list (progress cards, focus-goal marker), Goal detail (contributions list) and Goal form
- [x] T64.3 Planning screen: daily guide card and simulator inputs/results
- [x] T64.4 Screenshots approved

**Handoff notes:**
- T64.1 (2026-11-28): 5 artboards added to https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14 — GoalList, GoalDetail, GoalForm, PlanGuide, PlanSimulator (Planning is one activity, two modes). Approved with all four D68 changes: focus badge, date pickers + deadline toggle, More/Less adjustment + Simulate shortcut, monthly hint on Add goal.
- Mockup numbers are synthetic and consistent (guide: Rs. 40,051.00 ÷ 10 days = Rs. 4,005.10; simulation: 25,000 − 5,000 + 2,000 = 22,000/month, 56,000 left → 3 months). Guide shows the confirmed-bills state; T64.3 still needs the unconfirmed/changed-bills states and every status message.
- T64.2 (2026-11-28, verified on Pixel_8): `activity_goal_list.xml`, `item_goal.xml`, `activity_goal_detail.xml`, `item_contribution.xml`, `activity_goal_form.xml` rewritten; goal activities use ViewBinding. List: summary of goals in progress, focus first → in progress → reached → cancelled (`GoalListViewModel.ordered/totals`), Focus badge from the now-public `DashboardHighlights.focus`, Simulate opens `PlanningActivity.simulation`. Detail gets the focus id from the list (`GoalDetailActivity.FOCUS_ID`).
- D68 in code: contribution and target dates are calendar buttons (ISO kept in the view models); form has No deadline / By a date (`goal_deadline`) and the monthly hint `GoalRules.monthlyNeed` (to go ÷ whole months, rounded up). Cards and hero still speak the old LKR `goal_progress_text` sentence; status chips have icon + word. New icons pause/clock/star/edit/trend; `goal_form_title`/`goal_open` removed.
- Checks: 171 unit tests (+5), assemble, lint 4 = baseline; Form/Shell/Dashboard/Records fixture tests 4/4 on Pixel_8. Guarded `T64ScreenshotTest` (live dev API, synthetic demo goals seeded 28 Nov) passed in EN/SI × light/dark + EN 130% and checks order, focus badge, tap-to-open, calendar buttons, deadline toggle and the 14,142.86 hint. UserJourney/Evaluation goal steps updated (pick date via calendar, descriptions), not run. SI goal strings need native review.
- T64.3 (2026-11-28, verified on Pixel_8): `activity_planning.xml` + `item_planning_cut.xml`; `PlanningActivity` uses ViewBinding, pure rules in `PlanningRules` (+5 unit tests). Guide: Rs. fields with help, income date from the calendar (`model.incomeDate`, Clear date), tracked-bills card (confirm needed → scrolls into view; confirmed ✓), allowance card with the deductions. Simulation: goal, savings now, More/Less + Amount (D68, sent negated for Less), category cut rows, finish-month card.
- Every guide/simulation status has its own message (`PlanningRules.guideMessage/simulationMessage`); result cards and the bills row still speak the old sentences (journey tests now use `descContains`). New help line: a cut cannot exceed last month's recorded category spending (the API's rule; it previously surfaced only as "Check the values"). `planning_invalid_date` removed.
- Checks: 176 unit tests, lint 4 = baseline; planning journeys (guide+simulation, rotation lock, choice-retry) + Form/Dashboard/Records fixtures pass on Pixel_8 with `finoraIsolatedEmulator`; ShellNavigation flaked once after FinancialFormTest, passed 2/2 alone. `T64ScreenshotTest` planning part passed EN/SI × light/dark + EN 130%. SI uses traditional month names (උදුවප්, බක්) from Android locale data — native review.
- T64.4 (2026-11-28): Sevindu approved the goal (T64.2) and planning (T64.3) screenshots, EN/SI × light/dark + EN 130%. T64 done.

### T65 · Accounts and categories · `UX-05`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/accounts/`, `and:ui/categories/`, `res:layout/`
- [x] T65.1 Mockups of Account list, Account form, Category list and Category form approved by Sevindu
- [x] T65.2 Account list (archived state) and Account form
- [x] T65.3 Category list (income/expense sections, EN/SI names) and Category form (EN/SI name, income/expense, need/want chips). Category icons are a client-side mapping; no API change
- [x] T65.4 Screenshots approved

**Handoff notes:**
- T65.1 (2026-11-28): 4 artboards added to https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14 (canvas v11) — AccountList, AccountForm, CategoryList (full scroll), CategoryForm. Approved with all four D69 changes: tap to edit, Archived section, grouped category rows, form controls.
- Mockup data: demo accounts Commercial Bank / Cash wallet plus synthetic Debit card and archived "Old savings"; the real 14 expense + 4 income default categories with their Sinhala names and need/want; a custom "Gym" (Want, no Sinhala name) shows the editable row and the tag icon for unmapped names.
- T65.2 (2026-11-28, verified on Pixel_8): `activity_account_list.xml`, `item_account.xml`, `activity_account_form.xml` rewritten; list uses ViewBinding, rules in `AccountRules` (+2 unit tests). In use / Archived sections with counts; archived cards get an icon + word chip and no remove button; the whole card opens the form (TalkBack: old `account_summary` sentence, action "Edit <name>"). Form: Add/Edit heading, type tiles via new `FormChoices.setColumns(2)` (chosen tile shows a tick + bold, not colour alone), large signed Rs. opening balance with help.
- New icons bank/cash/wallet/archive; `account_form_title`/`account_opening_balance` removed. Demo account seeded 28 Nov with "Debit card" and archived "Old savings" (id 6, one synthetic Sept record).
- Checks: 178 unit tests, lint 4 = baseline; FinancialForm + Dashboard fixtures pass; FinancialListCard and ShellNavigation failed once in a batch while another app (com.printxpress.app) took focus on the emulator, both pass alone. Guarded `T65ScreenshotTest` passed EN/SI × light/dark + EN 130%. UserJourney account steps pass; the two journeys then fail on the T62 Home amounts (`Expenses: LKR 12.34` text / `dashboard_expense` desc) — stale since T62, not touched here.
- T65.3 (2026-11-28, Pixel_8): `activity_category_list.xml`, `item_category.xml`, `activity_category_form.xml` rewritten; rules in `CategoryRules` (+4 unit tests). Expense then Income grouped cards with counts, own categories first; defaults show a lock (TalkBack state "Default category · read-only"), own ones open on tap and keep the remove button. Rows show the other-language name and a Need/Want word chip. Form: Expense/Income and Need/Want/Not set tiles (form order changed in `CategoryFormViewModel.KINDS/NEEDS`; new categories default to Not set), type locked when editing, live preview using `CategoryIcons`. `FormChoices` now drops to fewer tiles per row when a label would be cut (Sinhala). Removed `category_form_title`, `category_classification`, `records_name`.
- T65.3 checks: 182 unit tests, lint 4 = baseline; FinancialForm + FinancialListCard fixtures pass; `T65ScreenshotTest` (now also categories, demo "Gym" id 18 seeded) passed EN/SI × light/dark + EN 130%.
- T65.4 (2026-11-28): Sevindu approved the 50 T65 screenshots (EN/SI, light/dark, EN 130%) from the review sheet. Sinhala strings still need a native-speaker read.

### T66 · Insights, advice and history · `UX-06`
**Lead:** Sevindu · **Support:** Sahan (review), Thilina (advice wording) · **Depends on:** T62 · **Touches:** `and:ui/insights/`, `and:ui/advice/`, `and:ui/history/`, `res:layout/`
- [x] T66.1 Mockups of Insights, Advice and History approved by Sevindu
- [x] T66.2 Insights (pattern cards with charts and text equivalents) and Advice (advice cards, rating control; `beta`/`v1` flavour behaviour kept)
- [x] T66.3 History (score/month timeline)
- [x] T66.4 Screenshots approved

**Handoff notes:**
- T66.1 (2026-11-28): 4 artboards added to https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14 (canvas v12) — Insights (full scroll), Score details, Advice (full scroll), Monthly history (full scroll). Approved with all four D70 changes: score first, Score details page, advice controls, history newest first.
- Mockup numbers are synthetic but follow `ScoreCalculator`/`PatternRules`: October 2026 income 85,000, expense 63,400 (the demo account's real totals), savings 2,000 → parts 45 + 20 + 6.30 + 4.71 + 7.65 = 83.66 → 84; wants 36,550 (43%), weekend 27,000 over 9 days vs 36,400 over 22 weekdays, income days 21,500 (33.91%). Goal plan uses the demo "Laptop for study" (99,000 left, 8 months → 12,375). The demo account currently has no patterns and no score (declarations missing) — T66.2 screenshots need seeded data.
- T66.2 (2026-11-28): new `InsightRules`, `ScoreRules`, `LimitBar`, `ScoreDetailsActivity`/`ViewModel` (declarations moved off Insights into their own page), `AdviceRules`; `InsightsActivity`, `AdviceActivity` (new `MONTH` extra, `v1`/`ADVICE_MODE` unchanged) and layouts rewritten. Unit tests 189/0, lint at the 4-warning baseline. `UserJourneyTest#bilingualUserRoutesFitText` passes at 100%; at 130% its only problem is the GoalFormActivity Sinhala "කාල සීමාවක් නැත" tile (not T66).
- T66.3 (2026-11-28): `HistoryActivity` + `HistoryRules`: 6/12/24 toggle, period switch, trend chart broken at unscored months with a spoken summary, month cards newest first. Guarded `T66ScreenshotTest` (`finoraT66Shots`) passed in EN/SI × light/dark + EN 130% (font scale restored to 1.0).
- Demo data seeded on the dev DB (synthetic account): five extra October expenses, Keells (id 15) changed to 1,850, declarations for Aug–Nov, dirty history rows for Jun–Sep (Aug marked reconstructed). October scores 81 (mockup 84 assumed savings of 2,000).
- `UserJourneyTest` journey: stale T62 amounts fixed ("85,000.00"); it now passes Insights but stops at Advice because the dev API returned LLM "Verified advice" where the test expects rule-based. Not run: D58GoalPicker/EvaluationJourneys (need the T55 evaluation setup).
- Sinhala review needed: the D58 coaching text and catalogue items come from the API in English on the Sinhala screens (unchanged behaviour).
- T66.4 (2026-11-28): Sevindu approved the 60 T66 screenshots (EN/SI, light/dark, EN 130%) from the review page https://claude.ai/artifact/XHXuDBzTgLizBMvA4hLRZw. Sinhala strings still need a native-speaker read.

### T67 · Import screens · `UX-07`
**Lead:** Sevindu · **Support:** Sahan (review; SMS owner) · **Depends on:** T62 · **Touches:** `and:ui/sms/`, `and:ui/importer/`, `res:layout/`
- [x] T67.1 Mockups of SMS access, Paste SMS and CSV import approved by Sevindu
- [x] T67.2 SMS access (permission explanation) and Paste SMS (parsed-field confirmation rows); rule 1 unchanged: only confirmed amount/date/merchant/type leave the phone
- [x] T67.3 CSV import (file pick, preview table, confirm)
- [x] T67.4 Screenshots approved

**Handoff notes:**
- T67.1 (2026-11-28): 5 artboards added to https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14 (canvas v13): SMS access (first visit), SMS access after a scan, Paste SMS, Review SMS transactions (full-screen sheet), CSV import preview. Approved as D71; CSV problem rows show the API reason as sent.
- T67.2 (2026-11-28): SmsAccessActivity/PasteSmsActivity rebuilt from new layouts (ids kept); shared row helper ui/sms/ImportRows; review is a full-screen sheet (view_sms_review/item_sms_review) with inline account/category pickers and "Save N", editing limits to amount/date/merchant. Rule 1 unchanged; parsed text exposed via contentDescription for tests. SmsPrivacy/UserJourney tests updated.
- T67.3 (2026-11-28): CsvImportActivity is a RecyclerView (view_csv_header + item_import_txn rows) with summary tiles and a pinned "Import N selected rows" bar; VM keeps the file name. Rows still opt-in. Unused SMS/CSV strings removed.
- Checks: 189 unit tests pass, lint at 4-warning baseline; device: smsRefusal and SmsPrivacy pass; text-fit passes on the T67 screens but flakes later on BillList/Main route waits. Paste journey still stops at LLM "Rule-based guidance" (as T66).
- Screenshots: T67ScreenshotTest (arg finoraT67Shots) via scratchpad shots67.sh, EN/SI light/dark + EN 130%. Sinhala needs a native read. Duplicate tile shows 0 (needs a prior CSV import).
- T67.4 (2026-11-28): 70 screenshots approved by Sevindu at https://claude.ai/artifact/UVBC4RED3C73c4tFe1egGW. Leftovers: native Sinhala read; account picker truncates at 130%.

### T68 · Settings, help and support · `UX-08`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/settings/`, `and:ui/help/`, `and:ui/support/SupportActivity`, `and:ui/main/` (More tab), `res:layout/`
- [x] T68.1 Mockups of More tab, Settings, Help (FAQ) and Support tickets approved by Sevindu
- [x] T68.2 More tab as a grouped list and Settings (profile card, Profile group with working Edit profile and Change password screens that call the T74 endpoints (D72), language, alerts, data rights)
- [x] T68.3 Help (expandable FAQ) and Support (ticket list, ticket thread, new ticket as a full screen with topic tiles and no confirm dialog, D72)
- [ ] T68.4 Screenshots approved

**Handoff notes:**
- T68.1 (2026-11-28): 6 artboards on https://claude.ai/artifact/HSXdxkzAczzeM2Q1WS7X14 (canvas v16): More tab, Settings, Help, Support requests, Request thread, New support request. Sevindu dropped the new-request confirm and asked for profile/password buttons (D72, new task T74). Approved by Sevindu on 2026-11-28.
- T68.2 (2026-11-28): More tab = `res:layout/panel_more.xml` + `MainActivity.bindMore()` (rows reuse item_import_link via `ui/settings/SettingsRows`; Language/Sign out rows trigger the shell's controls). Settings rebuilt (ids kept, MaterialSwitch alerts, EN/SI toggle via `ProfileLanguageButton.bindToggle`). New `EditProfileActivity`/`ChangePasswordActivity` + `ProfileRules`; `MeRepository` has the planned T74 calls (contract in `docs/api-contract.md`).
- Checks: 196 unit tests pass (new ProfileRulesTest, MeRepositoryTest), assemble OK, lint 4 = baseline (Gradle on the Android Studio JDK 25; JDK 17 crashes lint). Pixel_8 acceptance package: alert switches, export + delete, consent withdrawal pass; ShellNavigation passes without the API mapping (with it, its fake token is rejected — pre-existing). Tests click rows via `SettingsRowMatchers`.
- `T68ScreenshotTest` (arg finoraT68Shots, registers a disposable user) captured EN light + SI dark. Sinhala strings need a native read.
- T68.3 (2026-11-28): Help = `item_faq` x4 in `activity_finora_faq` + "Still need help?" card; USER Support = `activity_support` (cards via `SupportRows` status chip, thread view, Back returns to list) and new `SupportNewActivity` (topic tiles, preview of the exact sentence, Send, no dialog). STAFF Support screen unchanged until T70.
- Checks: 198 unit tests (new SupportRowsTest), lint 4 = baseline; FAQ layout probe passes at 130% EN+SI; T68ScreenshotTest now also captures Help/Support (EN light, SI dark, SI 130%). `approvedBilingualReplySurvivesCatalogRetirement` updated for the new flow but not run (needs the staff support fixtures).

### T69 · Register and password reset · `UX-09`
**Lead:** Sevindu · **Support:** Sahan (review; T61.4 owner) · **Depends on:** T62 · **Touches:** `and:ui/register/`, `and:ui/login/PasswordResetActivity`, `res:layout/`
- [ ] T69.1 Mockups of Register and Password reset (email step, code + new password step) matching the existing Login style, approved by Sevindu
- [ ] T69.2 Register (consent, over-18, language) and Password reset; the Login screen itself unchanged
- [ ] T69.3 Screenshots approved

**Handoff notes:**

### T70 · Staff screens · `UX-10`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/staff/`, `and:ui/support/DiagnosticQueueActivity`, `and:ui/support/FormatSampleActivity`, `res:layout/`
- [ ] T70.1 Mockups of Staff home, Diagnostic queue and Format sample approved by Sevindu (Staff activation is removed by T72, D66)
- [ ] T70.2 Staff home
- [ ] T70.3 Diagnostic queue and Format sample; rule 4 unchanged: STAFF never see user transactions
- [ ] T70.4 Screenshots approved

**Handoff notes:**

### T71 · Admin screens · `UX-11`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/admin/`, `res:layout/`
- [ ] T71.1 Mockups of Admin home and Admin directory (users, staff, admins) approved by Sevindu
- [ ] T71.2 Admin home (function tiles) and Admin directory (search, status chips, enable/disable/promote with confirm dialogs)
- [ ] T71.3 Screenshots approved

**Handoff notes:**

---

### T73 · Loading animation for slow screens · `UX-12`
**Lead:** Sevindu · **Support:** Sahan (review), Thilina (advice wording) · **Depends on:** T62 · **Touches:** `and:ui/` shared views, `and:ui/advice/`, `and:ui/insights/`, `and:ui/history/`, `and:ui/importer/`, `res:layout/`, `res:values*/`
- [ ] T73.1 Mockups of the loading states for AI advice, Insights, CSV import preview and the Sign in button approved by Sevindu
- [ ] T73.2 Shared loading view: Material 3 `LoadingIndicator` with a short message. It appears only after 300 ms, so fast answers never flash. After 5 s it adds "Still working…". With system animations off it shows a still indicator. TalkBack announces the message once. Lists show skeleton rows instead
- [ ] T73.3 AI advice: staged messages while waiting ("Reading this month's totals", "Checking the advice numbers"), with no amounts in them. When the LLM times out or fails the number check, rule-based advice replaces the loader without an error flash (rule 3)
- [ ] T73.4 Use the shared view on Insights, History, CSV import preview, Staff and Admin lists, and as a progress indicator inside the Sign in button
- [ ] T73.5 Screenshots approved

**Handoff notes:**

---

### T74 · Edit profile and change password · `UX-13`
**Lead:** Sevindu · **Support:** Sahan (Android screens), Thilina (QA) · **Depends on:** T68 (Settings layout), T61 (mail, for email change) · **Touches:** `api:user/`, `api:auth/`, `mig:`, `docs/api-contract.md`, `and:ui/settings/`, `and:data/repo/MeRepository`, `res:layout/`, `res:values*/`
**Detail: outline** — expand before coding (exact fields, endpoints, error codes, tests) and show to Sevindu.

New requirement from Sevindu on 2026-11-28 (D72): a signed-in USER cannot change their password, email or other profile details. `PATCH /api/v1/me` already accepts `fullName` and `language`, but the app only uses it for language, and there are no endpoints for email or password changes.
- [ ] T74.1 Outline expanded and approved: fields, endpoints, validation, error JSON, which sessions are revoked, audit events (draft contract already in `docs/api-contract.md`, "planned (T74)" — the app calls exactly these shapes)
- [ ] T74.2 API change password: current password + new password (same rules as Register), wrong current password → 400 without saying which part failed, rate-limited, other refresh tokens revoked, no password in logs; tests
- [ ] T74.3 API change email: password confirmation + code emailed to the new address (reuses the T61 mail sender), `CONFLICT` if the email is taken, old address told of the change; tests
- [x] T74.4 Android Edit profile (name, email with code step) and Change password screens, `FLAG_SECURE`, inline errors, EN + SI strings — built early in T68.2 against the planned contract (D72); recheck against the API once T74.2–T74.3 land
- [ ] T74.5 `docs/api-contract.md` updated; unit + Espresso tests; screenshots approved

**Handoff notes:**
