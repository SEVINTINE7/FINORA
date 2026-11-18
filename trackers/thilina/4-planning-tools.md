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

**Handoff notes:** —

---

### T32 · Savings goal simulator · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [ ] T32.1 [API] `POST /api/v1/simulate` `{goalId, monthlyChange, categoryCuts[]}` → new finish date, monthly amount; nothing saved
- [ ] T32.2 [AND·Sahan] Simulator screen, clearly marked "Simulation"

**Done when**
- [ ] Tests: rounding, zero change, impossible goal

**Handoff notes:** —
