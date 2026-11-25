# Sevindu 4 — Budgets, bills, goals, history (T17, T18, T19, T33)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand exact fields/endpoints/tests before coding.

---

### T17 · Monthly budgets · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10 · **Touches:** `api:budget/`, `and:ui/budgets/`
- [x] T17.1 [API] CRUD `/api/v1/budgets` per category per month (`YYYY-MM`)
- [x] T17.2 [API] `GET /api/v1/budgets?month=` returns limit, used, remaining, state `OK|NEAR(≥80%)|OVER`
- [x] T17.3 [AND·Sahan] Budget list with progress bars + add/edit
  - MD04 emulator forms verified list refresh after create/edit/delete and exact usage after transaction edits. The T17-specific physical-device checklist remains open.

**Done when**
- [x] Tests: used amount counts only that month's expenses in that category; first/last day of month; over-spend state

**Handoff notes:**
- API done (T17.1–T17.2). T17.3 code done by Sevindu: `and:ui/budgets/` (list + form + ViewModels), `data/api/BudgetDto`, `data/repo/BudgetRepository`, Plan tab → Budgets in `MainActivity`; device check (EN/SI, TalkBack, 409, rotation) still open.
- 2026-11-25 emulator acceptance (D57), Pixel 10 Pro XL AVD Android 17, `finora_test` V22, synthetic accounts: `UserJourneyTest#planningScreensBudgetsBillsGoals` 1/1 pass twice (EN + SI, rotation, TalkBack labels in tree; 409 via a stale form → duplicate message). Sevindu heard TalkBack on Budgets: OK. Landscape rows can sit below the fold (scroll), not a defect.
- `api:budget/`: `Budget`, repo, `BudgetService`, `BudgetController`, `dto/`. Spent amount from new `TransactionRepository.sumExpenseForCategory`.
- Budgets only on EXPENSE categories (default or own); another user's category → 400. One budget per category + month → 409 `CONFLICT`.
- `remaining` goes negative when over. used == limit is `NEAR` (OVER only when used > limit). 80% check uses multiplication, no division/rounding.
- Tests: `BudgetControllerTest` (9) incl. first/last day, leap February, 80%/100% edges; suite 87/87. `docs/api-contract.md` updated.


---

### T18 · Monthly bills · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T17 · **Touches:** `api:bill/`, `and:ui/bills/`
- [x] T18.1 [API] CRUD `/api/v1/bills` (amount, due_day, category)
- [x] T18.2 [API] Mark paid/unpaid per month; due_day 31 in short months = last day
- [x] T18.3 [API] Monthly bill total exposed for budgets/planning (T31)
- [x] T18.4 [AND·Sahan] Bills list with paid toggle and next due date
  - MD04 emulator forms verified list refresh, the paid toggle and dependent Insights and History refresh. The T18-specific physical-device checklist remains open.

**Done when**
- [x] Tests: February due dates, month rollover resets unpaid, paid twice → 409

**Handoff notes:**
- API done (T18.1–T18.3). T18.4 code by Sevindu: `and:ui/bills/` (list + form + ViewModels), `data/api/BillDto`, `data/repo/BillRepository`, Plan tab → Bills. Month switcher, summary line, paid checkbox (409 = already paid → reload), status Paid / Not paid / Overdue (due date before today, Asia/Colombo). Device check (EN/SI, TalkBack, rotation) still open.
- 2026-11-25 emulator acceptance (D57), Pixel 10 Pro XL AVD Android 17, `finora_test` V22, synthetic accounts: `UserJourneyTest#planningScreensBudgetsBillsGoals` 1/1 pass twice (EN + SI, rotation, TalkBack labels in tree; due day 31 → month end, paid toggle on/off updates summary). Sevindu heard TalkBack on Bills incl. checkbox state: OK.
- `api:bill/`: `Bill`, `BillPayment` (row = paid for that month; none = unpaid), repos, `BillService`, `BillController`, `dto/`.
- Paid: `POST /bills/{id}/paid?month=` (twice → 409), unpaid: `DELETE` same URL (idempotent 204). Category optional, must be EXPENSE.
- **For T21/T31:** `BillService.monthlyTotal(userId)` (BigDecimal, scale 2) and static `BillService.dueDate(bill, yearMonth)`. Endpoint `GET /bills/summary?month=` gives total/paid/unpaid.
- `active` column kept (always true for now); DELETE removes the bill. Tests: `BillControllerTest` (9); suite 96/96. `docs/api-contract.md` updated.


---

### T19 · Savings goals · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10 · **Touches:** `api:goal/`, `and:ui/goals/`
- [x] T19.1 [API] CRUD `/api/v1/goals` (target > 0, target date in future)
- [x] T19.2 [API] Contributions add/delete; progress = sum(contributions) / target, capped display at 100%
- [x] T19.3 [AND·Sahan] Goals list + detail with contributions
  - MD04 emulator forms verified list/detail refresh, goal lifecycle and contribution changes. T19-specific device acceptance remains open.

**Done when**
- [x] Tests: progress after edit/delete of contributions, invalid targets, no double counting

**Handoff notes:**
- API done (T19.1–T19.2). T19.3 code by Sevindu: `and:ui/goals/` (list → detail with add/remove contributions, edit/cancel/resume/delete; form), `GoalDto`, `GoalRepository`, Plan tab → Savings goals. Edit sends `cancelled: true` to keep a cancelled goal cancelled. **API gap:** D16's future-date check blocks any PUT (even cancel) once the target date passes; app asks the user to change/clear the date first. Dates typed as YYYY-MM-DD (no picker yet).
- `api:goal/`: `Goal`, `GoalStatus`, `GoalContribution`, repos, `GoalService`, `GoalController`, `dto/`. No migration (V3 tables).
- D16: target date future on create and edit; status automatic (DONE when saved ≥ target), user can only cancel via `cancelled: true`; contributions add/delete only. Add/delete contribution returns the updated goal.
- **For T23/T31/T32:** `GoalService.saved(goal)` (scale 2) and static `GoalService.progressPercent(saved, target)` (rounded down, max 100).
- Tests: `GoalControllerTest` (9); suite 105/105. `docs/api-contract.md` updated.
- 2026-11-25 re-verified on MySQL `finora_test` (V22): Budget/Bill/GoalControllerTest 27/27 pass; Android `testV1DebugUnitTest` 146/146. T17.3/T18.4/T19.3 still need the physical-device check.
- 2026-11-25 emulator acceptance (D57), Pixel 10 Pro XL AVD Android 17, `finora_test` V22, synthetic accounts: `UserJourneyTest#planningScreensBudgetsBillsGoals` 1/1 pass twice (EN + SI, rotation, TalkBack labels in tree; add/remove contributions 25%→35%→10%). Sevindu heard TalkBack on Goals list/detail: OK. Opt-in fixture: `stagePlanningTalkBackFixture`.

---

### T33 · Monthly insights and score history · `FR15`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T22, T23 · **Touches:** `api:history/`, `and:ui/history/`
- [x] T33.1 [API] Snapshot job: store month's insights + health score once (idempotent upsert on user+month)
- [x] T33.2 [API] Recalculate a month when its transactions change
- [x] T33.3 [API] `GET /api/v1/history?from&to` → score + key insights per month
- [x] T33.4 [AND·Sahan] History screen: score trend chart + month list

**Done when**
- [x] Tests: running snapshot twice = same rows; editing an old transaction updates that month's score

**Handoff notes:**
- D41 permits early implementation with T23 open. V10 preserves exact monthly amounts and nullable scores. Dirty months show pending until recalculation succeeds after commit, and phase-one D39 owner-lock ordering is retained.
- MySQL verified idempotence, the Colombo year boundary, rollback, a cross-month edit, exact JSON facts and API isolation. A scored past-month edit changed the saved 92 to the recalculated D20 score. A separate MySQL connection observed the worker block on the finance owner lock until commit.
- The isolated scheduler and emulator showed October net 866.85 then 766.85 after edit, a null score, and a separate 92/100 trend. Formal dependencies and independent review remain open.
- V13 implements D43 missed-month recovery with a persisted cutoff, bounded batches, durable retries and reconstruction provenance. The 322-test Maven run passed, including five new recovery tests and 27 D39 cases.
- An isolated scheduler recovered a synthetic September row with exact net LKR −9.77 and a null score. HMD displayed its Sinhala reconstruction notice and verified bilingual History return focus.
- Sevindu's independent review covers V10–V13 and the D39 owner lock. Production recovery requires the approved `FINORA_HISTORY_ACTIVATION_INSTANT` as described in [deployment](../../docs/deploy.md#history-recovery-activation-d43).
