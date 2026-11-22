# Thilina 4 — Daily guide and goal simulator (T31, T32)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T31 · Daily spending guide · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T17, T18, T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [ ] T31.1 [API] `(balance until next income − unpaid bills − planned savings) / days left`; next income date from user setting or last income + 1 month
- [ ] T31.2 [API] Return assumptions used; negative → 0 with warning flag
- [ ] T31.3 [AND·Sahan] Daily guide card + assumptions sheet

**Done when**
- [ ] Tests: last day of month, income today, no income history, negative balance

**Handoff notes:**
- 2026-11-22 audit: T17/T18/T19 remain in-progress; financial balance, next-income and planned-savings missing-data policy needs Sevindu's approval. No new formula or endpoint implemented; T31 remains todo.
- Later T54 readiness check: Account exposes openingBalance, not verified current cash; metrics supply recorded net and bills supply planned amounts/markers. No next-income setting or approved earmarking/day-count contract found. Plan: approve typed inputs/assumptions, then pure calculation and owner adapter. Cases income today, month end, no history, unknown balance and negative headroom need approved expected outcomes.

---

### T32 · Savings goal simulator · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [ ] T32.1 [API] `POST /api/v1/simulate` `{goalId, monthlyChange, categoryCuts[]}` → new finish date, monthly amount; nothing saved
- [ ] T32.2 [AND·Sahan] Simulator screen, clearly marked "Simulation"

**Done when**
- [ ] Tests: rounding, zero change, impossible goal

**Handoff notes:**
- 2026-11-22 audit: existing codex/t32-goal-simulator-api branch has no commits beyond its ecee49d base and no simulator implementation. T19 remains in-progress; baseline monthly savings, category-cut semantics and finish-date rounding require team approval before expanding/coding POST /api/v1/simulate. T32 remains todo.
- Later T54 readiness check: targets/dates and dated contributions exist; no approved recurring baseline, categoryCuts units/overlap or finish-date rule. Plan: approve request/response and pure write-free calculation before controller/UI. Cases zero change, already funded, cancelled/no-date, nonpositive capacity, fractional-month finish and duplicate/unaffordable cuts need approved outcomes; no policy implemented.
