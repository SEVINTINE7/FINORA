# Thilina 2 — Health score and rule-based advice (T23, T25)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding. Weights: `docs/decisions.md` P05.

---

### T23 · Health-score model (0–100) · `FR07`
**Lead:** Thilina · **Depends on:** T21, T18, T19, T01.3 · **Touches:** `api:score/`
- [ ] T23.1 Write each part's formula in `docs/decisions.md` (savings, spending control, debt payments, emergency savings, goal progress)
- [ ] T23.2 `ScoreService.forMonth()` → total + parts, each part clamped to its weight, total 0–100
- [ ] T23.3 Each part has a reason key + numbers (e.g. `SAVINGS_LOW {savedPct: 4.2}`) for EN/SI text
- [ ] T23.4 `GET /api/v1/score?month=`

**Done when**
- [ ] Tests: zero income, no goals, no debt, perfect month, worst month; same input → same output

**Handoff notes:** —

---

### T25 · Rule-based advice engine · `FR08`
**Lead:** Thilina · **Depends on:** T21, T22, T23 · **Touches:** `api:advice/rules/`
- [ ] T25.1 Rule catalogue: condition on facts → action (e.g. cut food delivery to X, move Y to savings)
- [ ] T25.2 Every number in the advice is computed from facts and listed as provenance
- [ ] T25.3 Output in the same structured format the AI must use (T26.2)

**Done when**
- [ ] Tests across budget, savings and debt scenarios; every number traceable to a fact

**Handoff notes:** —
