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
