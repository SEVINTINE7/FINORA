# Sevindu 9 — Screen-by-screen UI redesign (T62–T71)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`. Decisions D61, D65.

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
- [ ] T62.2 Shared kit: grouped LKR formatter (`Rs. 85,000.00`), section header with action link, transaction row (category icon, title, meta, signed amount), segmented type toggle, filter chip, extended FAB; unit tests for the formatter
- [ ] T62.3 Home (`DashboardPanel` → XML): greeting with `/me` name, month pill, navy balance card, score ring + "See advice", recent transactions + "See all", budgets with over-limit state, upcoming bills, focus goal, category donut + legend, 6-month trend with labels; loading/error/empty per section
- [ ] T62.4 Records (`TransactionActivity` → XML + RecyclerView): Records tab opens the list directly; All/Expense/Income toggle; Month/Account/Category chips replace the filter dialog; day groups with day totals; tap row → edit; swipe → Delete with confirm dialog (no undo); "Load more" replaces Previous/Next; Import icon → CSV import / SMS access; review mode kept
- [ ] T62.5 Add/Edit transaction (`activity_transaction_form.xml`): Expense/Income toggle, large amount with "Rs." prefix, category chips with icons, account dropdown, Material date picker, optional merchant/note, Save pinned at bottom; inline errors; uncertain-save review/retry kept
- [ ] T62.6 Screenshots (see Done when) shown to Sevindu for approval

**Handoff notes:**

### T63 · Budgets and bills · `UX-03`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/budgets/`, `and:ui/bills/`, `res:layout/`
- [ ] T63.1 Mockups of Budget list, Budget form, Bill list and Bill form approved by Sevindu
- [ ] T63.2 Budget list (progress cards, over-limit state) and Budget form (category chips, amount field, month)
- [ ] T63.3 Bill list (due-day badges, paid-this-month state and "Mark paid") and Bill form (name, amount, due day 1–31, category)
- [ ] T63.4 Screenshots approved

**Handoff notes:**

### T64 · Goals and planning tools · `UX-04`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/goals/`, `and:ui/planning/`, `res:layout/`
- [ ] T64.1 Mockups of Goal list, Goal detail, Goal form and Planning (daily guide + simulator) approved by Sevindu
- [ ] T64.2 Goal list (progress cards, focus-goal marker), Goal detail (contributions list) and Goal form
- [ ] T64.3 Planning screen: daily guide card and simulator inputs/results
- [ ] T64.4 Screenshots approved

**Handoff notes:**

### T65 · Accounts and categories · `UX-05`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/accounts/`, `and:ui/categories/`, `res:layout/`
- [ ] T65.1 Mockups of Account list, Account form, Category list and Category form approved by Sevindu
- [ ] T65.2 Account list (archived state) and Account form
- [ ] T65.3 Category list (income/expense sections, EN/SI names) and Category form (EN/SI name, income/expense, need/want chips). Category icons are a client-side mapping; no API change
- [ ] T65.4 Screenshots approved

**Handoff notes:**

### T66 · Insights, advice and history · `UX-06`
**Lead:** Sevindu · **Support:** Sahan (review), Thilina (advice wording) · **Depends on:** T62 · **Touches:** `and:ui/insights/`, `and:ui/advice/`, `and:ui/history/`, `res:layout/`
- [ ] T66.1 Mockups of Insights, Advice and History approved by Sevindu
- [ ] T66.2 Insights (pattern cards with charts and text equivalents) and Advice (advice cards, rating control; `beta`/`v1` flavour behaviour kept)
- [ ] T66.3 History (score/month timeline)
- [ ] T66.4 Screenshots approved

**Handoff notes:**

### T67 · Import screens · `UX-07`
**Lead:** Sevindu · **Support:** Sahan (review; SMS owner) · **Depends on:** T62 · **Touches:** `and:ui/sms/`, `and:ui/importer/`, `res:layout/`
- [ ] T67.1 Mockups of SMS access, Paste SMS and CSV import approved by Sevindu
- [ ] T67.2 SMS access (permission explanation) and Paste SMS (parsed-field confirmation rows); rule 1 unchanged: only confirmed amount/date/merchant/type leave the phone
- [ ] T67.3 CSV import (file pick, preview table, confirm)
- [ ] T67.4 Screenshots approved

**Handoff notes:**

### T68 · Settings, help and support · `UX-08`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/settings/`, `and:ui/help/`, `and:ui/support/SupportActivity`, `and:ui/main/` (More tab), `res:layout/`
- [ ] T68.1 Mockups of More tab, Settings, Help (FAQ) and Support tickets approved by Sevindu
- [ ] T68.2 More tab as a grouped list and Settings (profile, language, alerts, data rights)
- [ ] T68.3 Help (expandable FAQ) and Support (ticket list, ticket thread, new ticket)
- [ ] T68.4 Screenshots approved

**Handoff notes:**

### T69 · Register and password reset · `UX-09`
**Lead:** Sevindu · **Support:** Sahan (review; T61.4 owner) · **Depends on:** T62 · **Touches:** `and:ui/register/`, `and:ui/login/PasswordResetActivity`, `res:layout/`
- [ ] T69.1 Mockups of Register and Password reset (email step, code + new password step) matching the existing Login style, approved by Sevindu
- [ ] T69.2 Register (consent, over-18, language) and Password reset; the Login screen itself unchanged
- [ ] T69.3 Screenshots approved

**Handoff notes:**

### T70 · Staff screens · `UX-10`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/staff/`, `and:ui/support/DiagnosticQueueActivity`, `and:ui/support/FormatSampleActivity`, `res:layout/`
- [ ] T70.1 Mockups of Staff home, Staff activation, Diagnostic queue and Format sample approved by Sevindu
- [ ] T70.2 Staff home and Staff activation
- [ ] T70.3 Diagnostic queue and Format sample; rule 4 unchanged: STAFF never see user transactions
- [ ] T70.4 Screenshots approved

**Handoff notes:**

### T71 · Admin screens · `UX-11`
**Lead:** Sevindu · **Support:** Sahan (review) · **Depends on:** T62 · **Touches:** `and:ui/admin/`, `res:layout/`
- [ ] T71.1 Mockups of Admin home and Admin directory (users, staff, admins) approved by Sevindu
- [ ] T71.2 Admin home (function tiles) and Admin directory (search, status chips, enable/disable/promote with confirm dialogs)
- [ ] T71.3 Screenshots approved

**Handoff notes:**
