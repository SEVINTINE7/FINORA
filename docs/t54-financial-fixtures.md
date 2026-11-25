# T54 deterministic financial journeys

2026-11-22. Specification: D10, D18, D20 and existing budget/goal/API contracts.
Synthetic test data only. Implementation: `api-test:advice/FinancialJourneyTest.java`.
These tests extend individual unit tests by registering/logging in, posting records through actual HTTP
controllers, then checking database-derived dashboard, metrics, score, facts and mocked advice together.
Only the synthetic bill/goal created_at metadata is pinned through owner-scoped test JDBC updates to November 1;
this keeps the historical-month oracle reproducible in future runs. Financial records and changes use HTTP.
No production formulas changed. Exact-claim verification is tested as the current provisional policy, not approved here.

## Hand-calculated oracle

LKR; I income, N NEED expenses, W WANT expenses, S default Savings expense, B declared reserve.
E=N+W+S; C=N+W. Debt/goals explicitly declared not applicable in these six rows.
Applicable weight = 45+10+10=65; total unavailable if any applicable component is unavailable.

| Fixture | I | N | W | S | B | Spending / savings / reserve points | Total |
|---|---:|---:|---:|---:|---:|---|---:|
| Worker | 50000 | 30000 | 15000 | 1000 | 7500 | 45 / 4 / 2.50 | 79 |
| Small saver | 40000 | 38000 | 1600 | 400 | 0 | 30.60 / 2 / 0 | 50 |
| Unemployed | 0 | 10000 | 0 | 0 | 0 | unavailable / unavailable / 0 | unavailable |
| Break even | 100000 | 60000 | 40000 | 0 | 30000 | 27 / 0 / 5 | 49 |
| Cent rounding | 10000.01 | 5000.01 | 4000 | 499.99 | 1250 | 45 / 10 / 2.50 | 88 |
| No records | 0 | 0 | 0 | 0 | unknown | unavailable / unavailable / unavailable | unavailable |

- Worker: savings 10*1000/2500=4; B/N=.25 gives 2.50; 51.50/65*100→79.
  Additional saving is min(2500-1000,50000-46000)=1500, not the whole 4000 leftover.
- Small saver: C=39600; 27+18*(400/2000)=30.60. Supported saving 400 gives 2;
  32.60/65*100→50. E=I, so no extra-saving advice despite the low savings rate.
- Break even: C=I, so spending=27, not 45; wants exactly 40% produces no D10 high-wants alert.
  B/N=.5 gives 5; 32/65*100→49. No headroom exists for an extra allocation.
- Cent rounding: five-percent target before text rounding=500.0005; supported=499.99;
  savings points round HALF_UP to 10.00. Text target=500.00, shortfall=.01, available headroom=500.01,
  so additional-saving fact=.01. Reserve points round to 2.50; 57.50/65*100→88.
  Score rounding does not erase the factual one-cent shortfall.
- Unemployed: no income ratios or savings suggestions; recorded consumption deficit=10000, reserve milestone=2500.
- No records: no eligible advice and no provider call; a large opening balance never establishes emergency reserves.

Expected rule actions: worker/cent-rounding = reserve review + affordable saving; small saver/break-even = reserve
review; unemployed = deficit + reserve review; no-records = empty. Every numeric provenance value must equal
the owner-derived FactSheet with LKR and the selected month. Timeout returns exactly eligible canonical rules.

## Multi-step oracles

1. **Budget / spending boundary:** I=100000, N=60000, W=40000, no other expenses. Budget limit=40000:
   used=40000, remaining=0, existing state NEAR. Add a .01 WANT expense: used=40000.01,
   remaining=-.01, state OVER; D10 high-wants and .01 deficit advice become eligible.
   Inject an unsupported essential-food-cut guarantee into the current reply: reject UNSUPPORTED_CLAIM
   and return canonical rules. Delete the extra expense: original state/advice restored. Invalid negative
   expense is rejected and dashboard expense stays 100000.
2. **Bills / goals / ownership:** I=100000, N=60000, W=30000, S=5000, B=30000; loan bill=15000,
   initially unmarked; goal target=100000, contribution=25000. Points 45+0+3.75+10+5=63.75→64.
   Mark the bill paid and add another 25000 contribution: 45+20+7.50+10+5=87.50→88.
   Neither action creates a transaction: E remains 95000. Markers remain proxies, not verified repayments.
   Contradictory no-debt/no-goal declarations are rejected without altering the prior score.
   Exact eligible mocked advice passes. A separately registered user sees zero expense, empty advice and no
   verification event; no owner amounts, names, account labels or tokens enter their delivery.

## Boundaries and outstanding work

Analysis endpoints used: dashboard, score and (where asserted) insights; source metrics are checked through their
service. Advice remains service-only: no HTTP advice route or Android integration is claimed (T30).
There is no CSV import endpoint in this journey; records use supported manual transaction POSTs.
Missing planner policies are not treated as correctness oracles. Live-provider acceptance/pass rates, final
verifier approval and deployed/device privacy remain blocked. Final run counts and coverage live in the T54 tracker.

Final local verification: focused 8/8 and full Maven verify 265/265 passed; zero failures/errors/skips,
exit 0. Java 17, offline cached dependencies, isolated finora_test only. JaCoCo service lines 693/702
(98.72%); gate passed. git diff --check passed. Base 89b6e7c does not yet include open PR #10's four tests.
Evidence: api/target/t54-focused.log, api/target/t54-verify.log and target/site/jacoco/index.html.
