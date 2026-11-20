# Thilina 1 — Spending metrics and pattern detection (T21, T22)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding. Thresholds: `docs/decisions.md` D10.

---

### T21 · Deterministic spending metrics · `FR06`
**Lead:** Thilina · **Depends on:** T10, T17, T18 · **Touches:** `api:analytics/`, `api-test:analytics/`
- [x] T21.1 `MetricsService.forMonth(userId, YearMonth)` → income, expense, needs, wants, fixed bills, savings, payday-window spend, weekend vs weekday spend (all BigDecimal)
- [x] T21.2 Every metric carries its source: amount, period, transaction count
- [x] T21.3 Edge cases: no income (ratios = null, not divide-by-zero), refunds/reversals, uncategorised wants/needs

**Done when**
- [x] Unit tests with hand-calculated fixtures (write the expected numbers in the test)

**Handoff notes:**
- Done by Sevindu (with Claude) for Thilina — please review. No endpoint; service only.
- `api:analytics/`: `MetricsService.forMonth(userId, YearMonth)` → `MonthMetrics`; each money field is a `Metric {amount, from, to, count}`. New `TransactionRepository.findByUserIdAndTxnDateBetween`.
- D18: `leftOver` + `savedToCategory`, refunds = INCOME in 'Other', payday = income day + 2, `unclassified` bucket, ratios null when no income.
- **For T22/T23/T27:** use `wantsRatio`, `fixedBillsRatio` (compare with D10 0.40 / 0.50), `savingsRate`, `paydaySpend`, `weekendSpend`/`weekdaySpend`.
- Tests: `MetricsServiceTest` (6, Mockito, numbers in comments); suite 117/117.

---

### T22 · Spending-pattern detection · `FR06`
**Lead:** Thilina · **Depends on:** T21 · **Touches:** `api:analytics/`, `api:insight/`
- [x] T22.1 Rules from D10: high wants, payday spending, weekend spending, high fixed bills
- [x] T22.2 Each insight = `{type, facts{...numbers}, period}` saved in `insights`; text built later from facts
- [x] T22.3 `GET /api/v1/insights?month=` (USER)

**Done when**
- [x] Tests: normal, exactly-at-threshold, just-below, extreme and empty months

**Handoff notes:**
- Done by Sevindu (with Claude) for Thilina — please review (T21 too).
- `api:analytics/PatternRules` (pure rules on `MonthMetrics` → `SpendingPattern{type, facts}`); `api:insight/`: `Insight` (facts = JSON text), repo (bulk `@Modifying` delete — a derived delete ran after the new inserts), `InsightService`, `InsightController`, `dto/`.
- D19: payday > 30% of expense, weekend day avg > 1.5× weekday avg; all strict `>`; GET recalculates and replaces the month's rows.
- **For T23/T27:** MySQL JSON stores `6000.00` as `6000.0` — when reading facts back use `new BigDecimal(text).setScale(2)`. Facts contain numbers only (no merchants/names).
- Tests: `PatternRulesTest` (10: normal, exactly-at, just-below, one-cent-above, extreme, empty, no income), `InsightControllerTest` (5); suite 132/132. `docs/api-contract.md` updated.
