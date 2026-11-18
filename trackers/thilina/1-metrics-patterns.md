# Thilina 1 — Spending metrics and pattern detection (T21, T22)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding. Thresholds: `docs/decisions.md` P04.

---

### T21 · Deterministic spending metrics · `FR06`
**Lead:** Thilina · **Depends on:** T10, T17, T18 · **Touches:** `api:analytics/`, `api-test:analytics/`
- [ ] T21.1 `MetricsService.forMonth(userId, YearMonth)` → income, expense, needs, wants, fixed bills, savings, payday-window spend, weekend vs weekday spend (all BigDecimal)
- [ ] T21.2 Every metric carries its source: amount, period, transaction count
- [ ] T21.3 Edge cases: no income (ratios = null, not divide-by-zero), refunds/reversals, uncategorised wants/needs

**Done when**
- [ ] Unit tests with hand-calculated fixtures (write the expected numbers in the test)

**Handoff notes:** —

---

### T22 · Spending-pattern detection · `FR06`
**Lead:** Thilina · **Depends on:** T21 · **Touches:** `api:analytics/`, `api:insight/`
- [ ] T22.1 Rules from P04: high wants, payday spending, weekend spending, high fixed bills
- [ ] T22.2 Each insight = `{type, facts{...numbers}, period}` saved in `insights`; text built later from facts
- [ ] T22.3 `GET /api/v1/insights?month=` (USER)

**Done when**
- [ ] Tests: normal, exactly-at-threshold, just-below, extreme and empty months

**Handoff notes:** —
