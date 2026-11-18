# Sevindu 4 — Budgets, bills, goals, history (T17, T18, T19, T33)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand exact fields/endpoints/tests before coding.

---

### T17 · Monthly budgets · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10 · **Touches:** `api:budget/`, `and:ui/budgets/`
- [ ] T17.1 [API] CRUD `/api/v1/budgets` per category per month (`YYYY-MM`)
- [ ] T17.2 [API] `GET /api/v1/budgets?month=` returns limit, used, remaining, state `OK|NEAR(≥80%)|OVER`
- [ ] T17.3 [AND·Sahan] Budget list with progress bars + add/edit

**Done when**
- [ ] Tests: used amount counts only that month's expenses in that category; first/last day of month; over-spend state

**Handoff notes:** —

---

### T18 · Monthly bills · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T17 · **Touches:** `api:bill/`, `and:ui/bills/`
- [ ] T18.1 [API] CRUD `/api/v1/bills` (amount, due_day, category)
- [ ] T18.2 [API] Mark paid/unpaid per month; due_day 31 in short months = last day
- [ ] T18.3 [API] Monthly bill total exposed for budgets/planning (T31)
- [ ] T18.4 [AND·Sahan] Bills list with paid toggle and next due date

**Done when**
- [ ] Tests: February due dates, month rollover resets unpaid, paid twice → 409

**Handoff notes:** —

---

### T19 · Savings goals · `FR04`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10 · **Touches:** `api:goal/`, `and:ui/goals/`
- [ ] T19.1 [API] CRUD `/api/v1/goals` (target > 0, target date in future)
- [ ] T19.2 [API] Contributions add/delete; progress = sum(contributions) / target, capped display at 100%
- [ ] T19.3 [AND·Sahan] Goals list + detail with contributions

**Done when**
- [ ] Tests: progress after edit/delete of contributions, invalid targets, no double counting

**Handoff notes:** —

---

### T33 · Monthly insights and score history · `FR15`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T22, T23 · **Touches:** `api:history/`, `and:ui/history/`
- [ ] T33.1 [API] Snapshot job: store month's insights + health score once (idempotent upsert on user+month)
- [ ] T33.2 [API] Recalculate a month when its transactions change
- [ ] T33.3 [API] `GET /api/v1/history?from&to` → score + key insights per month
- [ ] T33.4 [AND·Sahan] History screen: score trend chart + month list

**Done when**
- [ ] Tests: running snapshot twice = same rows; editing an old transaction updates that month's score

**Handoff notes:** —
