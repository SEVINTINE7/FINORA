# Sahan 4 — Dashboard, main screens, insights, advice (T20, T38, T24, T30)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T20 · Dashboard and charts · `FR05`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T10, T17 · **Touches:** `and:ui/dashboard/`, `api:dashboard/`
**D40 Android scope:** Read `GET /api/v1/dashboard?month=YYYY-MM` with an authenticated USER request bound to the current login. Keep exact totals and category/month amounts in decimal form, provide text equivalents for charts, and reload Home after returning from Records. Verify the server fixture and keep the physical performance criterion separate.
- [x] T20.1 [API·Sevindu] `GET /api/v1/dashboard?month=` → income, expense, net, by-category totals, 6-month trend
- [x] T20.2 [AND] Dashboard: totals cards, category pie, trend bar chart (MPAndroidChart), month picker
- [x] T20.3 [AND] Empty state for new users

**Done when**
- [x] API test: dashboard totals equal sum of raw transactions for fixture data
- [x] Screen loads < 2 s with one year of data on a mid-range phone

**Handoff notes:**
- HMD Crest Android 14 matched dashboard fixture LKR 100000.00 income, 110000.22 expense and -10000.22 net; verified month switching and refresh after create, edit and delete. A local proxy verified connection failure and Retry, plus disabled month controls during a held response.
- `api:dashboard/`: `DashboardController`, `DashboardService`, `dto/` (`DashboardResponse`, `CategoryTotal`, `MonthTotal`). New `TransactionRepository.sumExpenseByCategory` (GROUP BY).
- D17: pie = expenses only (null category = one slice, app shows "Uncategorised" from strings.xml); `month` optional → current month (Asia/Colombo); trend = 6 months oldest first.
- Empty state (T20.3): new user gets 0.00 totals, `byCategory: []`, 6 zero trend months.
- MPAndroidChart v3.1.0 resolved from the module-scoped JitPack repository. Final HMD warm Records-to-Home loaded viewport was 505/504/486 ms with 27 synthetic records over 12 months, including USB capture overhead. Android passed 140 JVM tests, V1 debug, release manifest checks and lint with zero issues.

---

### T38 · Financial screens and navigation · `FR02–FR15`
**Lead:** Sahan · **Depends on:** T37, T09 · **Touches:** `and:ui/main/`, nav graph
**D40 Android scope:** Keep the saved five-tab selection, route to existing non-exported USER activities, and verify refresh after writes. History and help need actual destinations before final acceptance.
- [ ] T38.1 Bottom nav: Home (dashboard), Records, Plan (budgets/bills/goals), Insights, More (history, settings, help)
- [x] T38.2 Wire each screen as its API task finishes (track here which are wired)
- [x] T38.3 Lists refresh after create/update/delete everywhere

**Done when**
- [ ] Every screen reachable; no stale data after edits (manual checklist in Handoff notes)

**Handoff notes:**
- Home has the dashboard. Records links transactions, manual entry, CSV and SMS. Plan links the existing budget, bill and goal screens. Insights links the D20 screen. More links accounts, categories, settings and help.
- HMD Android 14 opened the implemented Records, Plan, Insights and More destinations and exercised Back. Home refreshed after transaction create, edit and delete and isolated two disposable logins. Prior emulator evidence covers selected-tab recreation.
- Isolated Android 17 verified every existing list after real API create, edit and delete with list re-entry: accounts, categories, transactions, budgets, bills and goals. Transaction mutations also refreshed Home and Insights. Plan selection survived landscape recreation.
- History still needs Sevindu's T33.3 API and T33.4 Android screen. Full physical form-driven mutation, rotation and every-screen acceptance remain open.
- History handoff needs USER-only, month-keyed score and insight snapshots, an approved month-range and missing-score contract, and recalculation for affected months including both months when a transaction changes date.

---

### T24 · Insights and health-score screens · `FR06 / FR07`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T22, T23, T38 · **Touches:** `and:ui/insights/`
**D40 Android scope:** Use `GET /api/v1/insights`, `GET /api/v1/score`, and `GET/PUT /api/v1/score-inputs/{month}`. Show server facts, five D20 components and explanations, with missing and not-applicable values kept distinct. Verify against backend fixtures and protect all requests with the current USER login.
- [x] T24.1 Insights list: each pattern with its numbers (amount, % of income, period)
- [x] T24.2 Score screen: total 0–100 + five parts, each with its reason
- [x] T24.3 "General guidance, not professional financial advice" note (EN/SI)

**Done when**
- [x] Screen values match Thilina's backend fixture results

**Handoff notes:**
- Isolated Android 17 displayed an unavailable D20 score, saved and reloaded a declaration, retained an unsaved 123.45 reserve through landscape recreation, and displayed the financial-guidance note. English/Sinhala resources contain the note.
- HMD Android 14 displayed all four patterns, exact fixture figures and periods, score 47 with two not-applicable components and score 52 with five scored components. The zero-income month displayed missing inputs and an unavailable score.
- A proxy withheld a committed declaration response. HMD showed uncertainty, one PUT was observed, and an explicit reload was required before editing and displayed the committed amount.
- Corrected dark-on-dark Insights and declaration text. HMD screenshots verified readable English and Sinhala Insights after the fix. Sahan confirmed TalkBack spoke Previous month and Next month correctly in both languages, in order.
- Isolated Android 17: a proxy dropped all three Insights reads; explicit Retry restored data. A held month response disabled month controls until completion. A positive response held across login replacement did not appear in the replacement user's empty Insights.

---

### T30 · Advice delivery and ratings · `FR09 / FR10`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T25, T29, T38 · **Touches:** `and:ui/advice/`, `api:advice/`
- [ ] T30.1 [API·Thilina] `GET /api/v1/advice?month=&mode=V1` — V1 returns AI if verified else RULE (D03/D40; contract pending)
- [ ] T30.2 [API·Thilina] `POST /api/v1/advice/{id}/rating` 1–5, once per advice per user
- [ ] T30.3 [AND] D03/D40 V1-only advice screen uses the server's verified result or rule fallback
- [ ] T30.4 [AND] `v1` flavor: one advice card with its supporting numbers + rating
- [ ] T30.5 [AND] Mode taken from `BuildConfig.ADVICE_MODE`, never from user input

**Done when**
- [ ] Tests: duplicate rating → 409; V1 falls back to RULE when check fails
- [ ] V1 advice and rating checked on a device

**Handoff notes:**
- Thilina's USER-facing delivery and rating controllers and published contracts remain pending. D40 retains V1-only architecture and existing ownership.
- Delivery needs an owner-scoped, stable advice ID for the selected month, accepted advice or verified rule fallback, exact supporting facts, and defined empty/error behavior. Rating needs owner validation, 1–5 validation, and concurrency-safe uniqueness per advice and user with 409 on duplicates. Confirm how Android reconciles a lost rating response before enabling writes.
