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
- 2026-11-24: T31 backend candidate implemented against b70c36a. Java 17/MySQL 8 full Maven verify: 345/345 PASS, including 23 T31 tests and 27 concurrency tests; zero failures/errors/skips. JaCoCo gate passed. V8-to-V13 database upgrade passed. Contract approval, persistent income-date policy, Android integration, formal dependencies and independent review remain open.

---

### T32 · Savings goal simulator · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [ ] T32.1 [API] `POST /api/v1/simulate` `{goalId, monthlyChange, categoryCuts[]}` → new finish date, monthly amount; nothing saved
- [ ] T32.2 [AND·Sahan] Simulator screen, clearly marked "Simulation"

**Done when**
- [ ] Tests: rounding, zero change, impossible goal

**Handoff notes:**
- 2026-11-22 audit: existing codex/t32-goal-simulator-api branch has no commits beyond its ecee49d base and no simulator implementation. T19 remains in-progress; baseline monthly savings, category-cut semantics and finish-date rounding require team approval before expanding/coding POST /api/v1/simulate. T32 remains todo.

Review evidence: T31 draft PR #14 retains 0a1f4e5. The integration run on 2026-11-24 passed all 23 daily-guide tests within 129 focused backend tests on isolated finora_test. Contract approval, persistent-date policy and MD05 review gates remain open.
