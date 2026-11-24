# FINORA T32 - Savings Goal Simulator

Status: Backend verified; proposed financial policy; team review pending.
Owner: Thilina. Android integration: Sahan.

## API
POST /api/v1/simulate
Requires authenticated USER role.

Request fields:
- goalId: positive goal ID owned by the user.
- baselineMonthlySavings: required, nonnegative LKR amount.
- monthlyChange: signed monthly savings adjustment.
- categoryCuts: required array, maximum 20 items.
- Each cut contains categoryId and positive monthlyReduction.

## Proposed calculation policy
Remaining = max(0, target amount - saved contributions).
Monthly amount = baseline + monthlyChange + sum(category cuts).
Category cuts must not also be included in monthlyChange.
Baseline is explicitly provided, not inferred from historical surplus.
Cuts cannot exceed recorded spending for the previous complete month.
Only accessible, active expense categories qualify.
Duplicate category cuts are rejected.
Reference timezone: Asia/Colombo.

If the goal is complete: ALREADY_COMPLETE.
If monthly amount is nonpositive: NO_MONTHLY_CAPACITY.
Otherwise months = ceiling(remaining / monthly amount).
First assumed contribution: end of next calendar month.
Beyond 1200 months: BEYOND_HORIZON.
Completion dates are hypothetical, never guaranteed.

## Android handoff

Label the screen and result “Simulation” in English and Sinhala. Prompt for the baseline and
explain that the signed adjustment excludes separately listed reductions. Show the reference
month and assumptions. Keep `monthsRequired` and `hypotheticalFinishDate` unavailable when
the server supplies null. Render the completed-goal and no-capacity states separately, and
discard stale responses after session changes. No save action is required by this API.

Example request: `{"goalId":42,"baselineMonthlySavings":3000.00,"monthlyChange":-500.00,"categoryCuts":[]}`.
For a synthetic owner goal with LKR 10,000 target and LKR 5,000 saved on Colombo 2026-11-24,
the result has `hypotheticalMonthlySavings: 2500.00`, `monthsRequired: 2`,
`hypotheticalFinishDate: "2027-01-31"`, and `spendingReferenceMonth: "2026-10"`.

Use the standard error envelope. Invalid inputs, duplicate/excessive cuts and cancelled goals
return 400. Missing or inaccessible resources return 404. Missing authentication returns 401;
ADMIN/STAFF return 403. Keep MD05 device acceptance and implementation approval separate.

## Security and persistence
Only the authenticated user's goal and expenses are accessed.
Foreign and missing goals both return 404.
Cancelled goals cannot be simulated.
Simulation must not modify financial records.

## Verification - 2026-11-22
30 focused tests passed.
287 full backend tests passed; zero failures, errors or skips.
Maven verify exit code 0; JaCoCo service coverage gate passed.
Verified with the isolated MySQL finora_test schema.

## Remaining acceptance
Financial assumptions require team PR review.
Android Simulation screen and end-to-end integration remain pending.
Do not mark the complete T32 feature accepted yet.
