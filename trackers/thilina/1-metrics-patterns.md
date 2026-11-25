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
- 2026-11-22 Thilina reviewed the existing service and reproduced two defects: prior-month payday income was ignored; custom categories named Savings qualified despite D18's default-category rule.
- `api:analytics/MetricsService.java` now reads only the caller's preceding two days as payday context (not monthly totals/counts) and requires a default EXPENSE Savings category. Existing formulas/thresholds are unchanged.
- Regression evidence: 2 new tests failed before correction; after correction MetricsServiceTest (12), PatternRulesTest (13), InsightControllerTest (10): 35 run, 0 failures/errors/skips, Java 17, isolated MySQL 8 `finora_test` only.
- Evidence: `api/target/t21-regression-before.log`, `api/target/t21-t22-review.log` and Surefire reports (ignored build output). T50 was not rerun.
- Next: review the feature-branch fixes in the upstream PR; status returned to review for the new correction, not falsely marked accepted. Backend dependencies' existing API contracts were preserved.
- Execution 29 carries the D55 semantic code consumer from 94e8335. Focused D55 verification covered the rename/deactivation and history regressions. Final review and dependency closure remain open. Confirm whether derived ratios require standalone provenance.

---

### T22 · Spending-pattern detection · `FR06`
**Lead:** Thilina · **Depends on:** T21 · **Touches:** `api:analytics/`, `api:insight/`
- [x] T22.1 Rules from D10: high wants, payday spending, weekend spending, high fixed bills
- [x] T22.2 Each insight = `{type, facts{...numbers}, period}` saved in `insights`; text built later from facts
- [x] T22.3 `GET /api/v1/insights?month=` (USER)

**Done when**
- [x] Tests: normal, exactly-at-threshold, just-below, extreme and empty months

**Handoff notes:**
- 2026-11-22 Thilina reviewed D10/D19 rule comparisons, numeric facts, month replacement and ownership. No additional rule/endpoint implementation defect confirmed; existing done status preserved.
- Added integration coverage for payday windows crossing a month boundary and isolation from another user's prior-month income; only current-month expenses contribute to facts.
- Focused verification after the T21 fix: MetricsServiceTest (12), PatternRulesTest (13), InsightControllerTest (10), all 35 passing on Java 17/MySQL `finora_test`; output in `api/target/t21-t22-review.log`.
- Thresholds remain strict >; MySQL JSON numeric scale may differ, so downstream readers must normalize BigDecimal scale. Facts exclude names/merchants.
- Next: upstream review of the T21 dependency correction; continue D20 backend scoring under the user's explicit backend-only authorization without changing T18/T19 statuses.
