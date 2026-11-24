# Thilina 2 — Health score and rule-based advice (T23, T25)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: T23/T25 expanded below** — weights and formulas: `docs/decisions.md` D20 (approved, formerly P05).

---

### T23 · Health-score model (0–100) · `FR07`
**Lead:** Thilina · **Depends on:** T21, T18, T19, T01.3 · **Touches:** `api:score/`, `api-test:score/`, `mig:V7__score_inputs.sql`, `docs/api-contract.md`, `docs/db-schema.md`
- [x] T23.1 Use the approved five-part formulas in `docs/decisions.md` D20; no new scoring thresholds
- [x] T23.2 Pure BigDecimal calculator + `ScoreService.forMonth()` → total/parts; points clamped to weights, total 0–100, missing total null, explicit N/A weights normalized
- [x] T23.3 Stable reason keys + aggregate numeric facts for EN/SI; debt paid markers and user-declared reserves explicitly labeled
- [x] T23.4 USER-only `GET /api/v1/score?month=YYYY-MM`; no user id accepted; validate month and preserve ownership/role privacy
- [x] T23.5 Minimal owner-only GET/PUT `/api/v1/score-inputs/{month}`: nullable reserve/date and no-applicable-debt/goals declarations, UTC confirmation time; V7 schema, validation and contradiction checks
- [ ] T23.6 Team review and remaining T18/T19 dependencies resolved before full task acceptance; no teammate status changed

**Done when**
- [x] Tests: zero income, no goals, no debt, perfect month, worst month; same input → same output

**Handoff notes:**
- 2026-11-22 Thilina explicitly authorized backend-only T23 against completed T18/T19 APIs and the minimal monthly input endpoint; final acceptance still waits for dependencies/team review.
- D20 implemented in `api:score/` with V7 declarations only, no score history writes or inferred balances. Existing goal/bill production classes and teammate statuses unchanged.
- New tests: ScoreCalculatorTest 26 and ScoreControllerTest 12 passed; combined analytics/insight/schema verification: 84 run, 0 failures/errors/skips, Java 17 + isolated MySQL `finora_test` only; evidence `api/target/t23-d20-verification.log` and Surefire reports.
- Dates/limitations: only contributions through month end and plans created by month end qualify; current bill/goal edits may change historical results; reserve date remains as declared, not inferred month-end. Numeric facts carry no names/merchants.
- Final UTC DTO check: 38 scoring tests passed (`api/target/t23-final-check.log`). Next: feature-branch PR review (stacked on T21 PR #5); keep T23 in-progress until dependency/human acceptance. User separately authorized backend T25/T26 continuation; T50 was not repeated.

---

### T25 · Rule-based advice engine · `FR08`
**Lead:** Thilina · **Depends on:** T21, T22, T23 · **Touches:** `api:advice/rules/`, shared `api:advice/ai/` DTOs, `api-test:advice/`, `docs/ai-schema.md`
- [x] T25.1 Approved catalogue: REVIEW_SPENDING_DEFICIT, REVIEW_HIGH_WANTS, REVIEW_UNMARKED_LOAN_BILLS, BUILD_EMERGENCY_BUFFER, REVIEW_GOAL_PROGRESS, SAVE_WITHIN_AVAILABLE_HEADROOM; conditions use D10/D20 only (see docs/ai-schema.md)
- [x] T25.2 Every text number has an exact factKey/value/LKR/period reference; extra allocations bounded by leftover after existing savings and suppressed for unknown/unmarked debt
- [x] T25.3 Shared `AdviceItem {actionId,text,numbers[]}` schema for rules and verified AI. The server-only RuleAdviceService feeds T30 V1 delivery.

**Done when**
- [ ] Tests across budget, savings and debt scenarios; every number traceable to a fact

**Handoff notes:**
- 2026-11-22 Thilina explicitly approved backend T25/T26 continuation before T23 acceptance and the six-action catalogue; formulas/conditions remain D10/D20, no new financial thresholds.
- `RuleAdviceEngine` and `RuleAdviceService` generate structured advice from server aggregates; advice protects essentials and labels bill markers as proxies, never verified repayments.
- `RuleAdviceTest` 11 + `FactSheetPrivacyTest` 1 = 12 passing tests (0 failures/errors/skips), Java 17 and isolated `finora_test`; evidence `api/target/t25-t26-tests.log`.
- Every numeric token has tested exact provenance; saving suggestions use min(shortfall, recorded leftover) and are blocked for unknown/unmarked obligations. No inferred reserves or added goal allocations.
- T30 now delivers canonical rule fallback or verified advice through its [Sahan tracker entry](../sahan/4-screens.md). Budget-specific coverage and Thilina's review remain open.
