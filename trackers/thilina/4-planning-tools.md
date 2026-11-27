# Thilina 4 — Daily guide and goal simulator (T31, T32)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T31 · Daily spending guide · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T17, T18, T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [x] T31.1 [API] `(balance until next income − unpaid bills − planned savings) / days left`; next income date from user setting or last income + 1 month
- [x] T31.2 [API] Return assumptions used; negative → 0 with warning flag
- [x] T31.3 [AND·Sahan] Daily guide card + assumptions sheet

**Done when**
- [x] Tests: last day of month, income today, no income history, negative balance

**Handoff notes:**
- 2026-11-22 audit: T17/T18/T19 remain in-progress; financial balance, next-income and planned-savings missing-data policy needs Sevindu's approval. No new formula or endpoint implemented; T31 remains todo.
- Later T54 readiness check: Account exposes openingBalance, not verified current cash; metrics supply recorded net and bills supply planned amounts/markers. No next-income setting or approved earmarking/day-count contract found. Plan: approve typed inputs/assumptions, then pure calculation and owner adapter. Cases income today, month end, no history, unknown balance and negative headroom need approved expected outcomes.
- 2026-11-24: T31 backend candidate implemented against b70c36a. Java 17/MySQL 8 full Maven verify: 345/345 PASS, including 23 T31 tests and 27 concurrency tests; zero failures/errors/skips. JaCoCo gate passed. V8-to-V13 database upgrade passed. Contract approval, persistent income-date policy, Android integration, formal dependencies and independent review remain open.
- [PR #14](https://github.com/SEVINTINE7/FINORA/pull/14) contains the backend candidate and proposed contract at `109a151`. Android T31.3 remains unchecked pending approved financial/input semantics and formal T17/T18/T19 closure or a documented early start. The candidate is separate from draft PR #16.
- 2026-11-27 tracker sync (Sevindu): daily-guide code is on `main`, but T31.1/T31.2 stay unchecked. Local run: 40/41 daily-guide tests passed; `DailyGuideServiceTest.validBillsCanProduceAnAggregateThatTheConfirmationFieldRejects` (line 83) fails because it still expects `@Digits` to reject a 14-digit aggregate that `29a92f4` now accepts. Thilina to decide whether the test or the field limit is correct.
- 2026-11-27 resolved: PR #23's assertion was merged after the PR #28 fix and described the pre-fix behaviour; updated it to expect the exact aggregate to be accepted (Sevindu merged #28 as the approved correction). Full `mvnw verify`: 503 run, 0 failures/errors, 1 skipped, JaCoCo gate met; daily-guide 28/28. T31.1/T31.2/tests checked; T31.3 Android card and contract sign-off remain.

- 2026-11-27 integration update: merged [PR #30](https://github.com/SEVINTINE7/FINORA/pull/30) includes the Android Daily Guide and assumptions/confirmation controls. Its tested `264c1ae` revision passed the dedicated emulator planning/retry/rotation journeys; the LKR 4,700.00 allowance was observed. T17/T18/T19 are now `done`. Preserve T31.3 as open for Sahan's review and final contract acceptance; implementation is not missing.

- 2026-11-27 acceptance reconciliation: Thilina reports the requested team confirmation of T31.3 against the merged PR #30 implementation and retained emulator/PR #39 handset evidence. T17/T18/T19 are done. Successful API job 140646145075 records DailyGuideServiceTest 2/2, DailyGuideControllerTest 11/11, DailyGuideAggregateConfirmationTest 2/2 and DailyGuideMathTest 13/13. Propose T31 done on the reported confirmation; original approval messages were not independently inspected. No new tests or financial-contract changes.

---

### T32 · Savings goal simulator · `FR14`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T19 · **Touches:** `api:planning/`, `and:ui/planning/`
- [x] T32.1 [API] `POST /api/v1/simulate` `{goalId, monthlyChange, categoryCuts[]}` → new finish date, monthly amount; nothing saved
- [x] T32.2 [AND·Sahan] Simulator screen, clearly marked "Simulation"

**Done when**
- [x] Tests: rounding, zero change, impossible goal

**Handoff notes:**
- 2026-11-22 update: T32 backend implemented in ff03255; PR #13 open. 30 focused tests and 287 full backend tests passed; JaCoCo gate passed. Financial policy is proposed in docs/t32-simulator-contract.md. Team review and Android integration pending; overall T32 remains in-progress.
- 2026-11-22 audit: existing codex/t32-goal-simulator-api branch has no commits beyond its ecee49d base and no simulator implementation. T19 remains in-progress; baseline monthly savings, category-cut semantics and finish-date rounding require team approval before expanding/coding POST /api/v1/simulate. T32 remains todo.
- Later T54 readiness check: targets/dates and dated contributions exist; no approved recurring baseline, categoryCuts units/overlap or finish-date rule. Plan: approve request/response and pure write-free calculation before controller/UI. Cases zero change, already funded, cancelled/no-date, nonpositive capacity, fractional-month finish and duplicate/unaffordable cuts need approved outcomes; no policy implemented.

Review evidence: T31 draft PR #14 retains 0a1f4e5. The integration run on 2026-11-24 passed all 23 daily-guide tests within 129 focused backend tests on isolated finora_test. Contract approval, persistent-date policy and MD05 review gates remain open.
- [PR #13](https://github.com/SEVINTINE7/FINORA/pull/13) contains the backend candidate and simulator contract at `d59e94f`. Android T32.2 remains unchecked pending approved savings/cut and date semantics and formal T19 closure or a documented early start. The candidate is separate from draft PR #16.
- 2026-11-27 tracker sync (Sevindu): simulator code is on `main` (`api:planning/Simulation*`). Simulation tests 31/31 passed locally (Controller 13, DatabaseIntegration 3, Math 8, Service 7), covering rounding, zero capacity and unreachable goals. T32.2 Android screen and team approval of the contract remain open.
- 2026-11-27 integration update: merged [PR #30](https://github.com/SEVINTINE7/FINORA/pull/30) includes the Android Simulator. Connected evidence at `264c1ae` covers the LKR 20,000.00 hypothetical monthly saving, held-response rotation and recovery after a category-load failure. T19 is now `done`. Keep T32.2 open for Sahan's review and contract acceptance; no physical-handset claim.

- 2026-11-27 acceptance reconciliation: Thilina reports the requested team confirmation of T32.2 against the merged PR #30 implementation and retained emulator/PR #39 handset evidence. T19 is done. Successful API job 140646145075 records SimulationControllerTest 13/13, SimulationDatabaseIntegrationTest 3/3, SimulationMathTest 8/8 and SimulationServiceTest 7/7. Propose T32 done on the reported confirmation; original approval messages were not independently inspected. No new tests or financial-contract changes.
