# Sahan 4 — Dashboard, main screens, insights, advice (T20, T38, T24, T30)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T20 · Dashboard and charts · `FR05`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T10, T17 · **Touches:** `and:ui/dashboard/`, `api:dashboard/`
**D40 Android scope:** Read `GET /api/v1/dashboard?month=YYYY-MM` with an authenticated USER request bound to the current login. Keep exact totals and category/month amounts in decimal form, provide text equivalents for charts, and reload Home after returning from Records. Verify the server fixture and keep the physical performance criterion separate.
- [x] T20.1 [API·Sevindu] `GET /api/v1/dashboard?month=` → income, expense, net, by-category totals, 6-month trend
- [ ] T20.2 [AND] Dashboard: totals cards, category pie, trend bar chart (MPAndroidChart), month picker
- [ ] T20.3 [AND] Empty state for new users

**Done when**
- [x] API test: dashboard totals equal sum of raw transactions for fixture data
- [ ] Screen loads < 2 s with one year of data on a mid-range phone

**Handoff notes:**
- API part done (T20.1); **T20.2–T20.3 (Android) still open — blocked by T37**. Screen-load check (< 2 s) also waits for the app.
- `api:dashboard/`: `DashboardController`, `DashboardService`, `dto/` (`DashboardResponse`, `CategoryTotal`, `MonthTotal`). New `TransactionRepository.sumExpenseByCategory` (GROUP BY).
- D17: pie = expenses only (null category = one slice, app shows "Uncategorised" from strings.xml); `month` optional → current month (Asia/Colombo); trend = 6 months oldest first.
- Empty state (T20.3): new user gets 0.00 totals, `byCategory: []`, 6 zero trend months.
- Tests: `DashboardControllerTest` (6); suite 111/111. `docs/api-contract.md` updated.

---

### T38 · Financial screens and navigation · `FR02–FR15`
**Lead:** Sahan · **Depends on:** T37, T09 · **Touches:** `and:ui/main/`, nav graph
**D40 Android scope:** Keep the saved five-tab selection, route to existing non-exported USER activities, and verify refresh after writes. History and help need actual destinations before final acceptance.
- [ ] T38.1 Bottom nav: Home (dashboard), Records, Plan (budgets/bills/goals), Insights, More (history, settings, help)
- [ ] T38.2 Wire each screen as its API task finishes (track here which are wired)
- [ ] T38.3 Lists refresh after create/update/delete everywhere

**Done when**
- [ ] Every screen reachable; no stale data after edits (manual checklist in Handoff notes)

**Handoff notes:** —

---

### T24 · Insights and health-score screens · `FR06 / FR07`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T22, T23, T38 · **Touches:** `and:ui/insights/`
**D40 Android scope:** Use `GET /api/v1/insights`, `GET /api/v1/score`, and `GET/PUT /api/v1/score-inputs/{month}`. Show server facts, five D20 components and explanations, with missing and not-applicable values kept distinct. Verify against backend fixtures and protect all requests with the current USER login.
- [ ] T24.1 Insights list: each pattern with its numbers (amount, % of income, period)
- [ ] T24.2 Score screen: total 0–100 + five parts, each with its reason
- [ ] T24.3 "General guidance, not professional financial advice" note (EN/SI)

**Done when**
- [ ] Screen values match Thilina's backend fixture results

**Handoff notes:** —

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

**Handoff notes:** Thilina's USER-facing advice delivery and rating controllers and published response contract are pending. D40 retains the V1-only architecture and does not change ownership.
