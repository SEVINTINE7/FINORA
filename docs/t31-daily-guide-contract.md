# T31 daily spending guide — proposed integration contract

FR14. Lead: Thilina. Android: Sahan. Candidate based on b70c36a source context.
Sahan's scoped Android-side approval was relayed on 2026-11-24; no approval for another
owner, dependency closure or shared MD05 start is implied. Record a formal early-start
decision with the next available identifier after the appropriate owners confirm.
Do not mark T31 accepted based on this patch. Original T17/T18/T19 gates remain.

## Inputs and endpoint

POST /api/v1/daily-guide, authenticated USER only, JSON. Read-only calculation despite POST;
no persistent settings, financial data updates, provider calls or inferred bank balance.
All request fields nullable. Exact LKR decimals (max 13 integer, 2 fractional digits);
only availableFunds can be negative. Date uses YYYY-MM-DD. User supplies availableFunds
(current spendable funds before the remaining deductions), plannedSavings (not yet deducted),
nextIncomeDate (optional override), confirmedTrackedUnpaidBills (confirmation of the
server-calculated total), additionalUnpaidBills (older arrears/untracked commitments not yet
deducted, explicitly zero if none). A request may be submitted without confirmation to show
tracked bills; if a later request's confirmation differs from the recalculated amount, no
allowance is returned. A matching total is not confirmation of identical bill identities.
The user must reconcile paid markers, prior arrears and any double counting before confirming.
The date fallback uses the latest recorded INCOME dated through today plus one calendar month;
not verified salary or predicted arrival. May include refunds (D18). No automatic rolling
of stale dates. Dates in the past or today produce unavailable status. Explicit future horizon
bounded to 366 days; this and per-request rather than persistent settings are proposed design
choices. Persistent income-date settings are not implemented and remain a contract decision.

## Calculation

Colombo date. Spendable = availableFunds - trackedUnpaidBills - additionalUnpaidBills - plannedSavings.
Tracked amount = unpaid active recurring bills in the current month (including overdue) and
later months with due date on or before next income date. Older-month obligations are not
inferred from today's recurring bill definitions; user declares remaining arrears separately.
No budget limits or goal contributions are treated as cash deductions. Bill markers are not
bank evidence. The supplied funds must not already exclude those same deductions.
Days = today inclusive to next-income date exclusive. Reserve bills due on income day as a
conservative policy; divide only if days > 0. Round DOWN to cents. Negative remainder gives
zero allowance plus positive shortfall. Missing inputs give null allowance, not zero.
No assertion that remaining money is safe for all real-life needs.

## Response and Android acceptance

Fields: asOf, nextIncomeDate (nullable), incomeDateSource (USER_SELECTED,
LAST_RECORDED_INCOME_PLUS_ONE_MONTH, UNAVAILABLE), lastRecordedIncomeDate (nullable),
availableFunds, trackedUnpaidBills, trackedUnpaidBillCount, additionalUnpaidBills,
plannedSavings, status, spendingDays, spendableFunds, dailyAllowance, shortfall, warning,
assumptions (stable keys, localized in Android). Monetary and count fields may be null
when unavailable. Status READY, NO_SPENDABLE_FUNDS, SHORTFALL, MISSING_BALANCE,
MISSING_PLANNED_SAVINGS, MISSING_NEXT_INCOME_DATE, INCOME_TODAY, INCOME_DATE_PASSED,
BILL_CONFIRMATION_REQUIRED, BILLS_CHANGED, MISSING_ADDITIONAL_BILLS.

Example request (synthetic):
    {"availableFunds":"1000.00","plannedSavings":"100.00","nextIncomeDate":"2026-11-25",
     "confirmedTrackedUnpaidBills":"200.00","additionalUnpaidBills":"50.00"}
On Colombo 2026-11-24 with tracked bills 200.00, status READY, spendingDays 1,
spendableFunds 650.00, dailyAllowance 650.00, shortfall 0.00, warning false.

HTTP 400 invalid amount/date, 401 anonymous/invalid token, 403 ADMIN/STAFF. Normal FINORA
error envelope. No raw SMS, merchant, account numbers or provider payloads. Do not pass a user ID.
Android: preserve draft within active session, display explicit date assumption, bill total and
confirmation, allow additional arrears and explicit zero, show null as unavailable, distinguish
zero/shortfall from missing, refresh after financial changes, suppress stale responses after
logout or account switch, EN/SI text, TalkBack and rotation acceptance. No automatic saves.

## Evidence and open gates

13 pure calculation unit tests and 10 controller/MySQL test methods are supplied. Actual
Maven/MySQL execution, coverage, full integrated regression, finance-owner review, Android
implementation, device checks and formal dependencies are still pending. The snapshot uses
read-only REPEATABLE_READ with BillService and the existing owner-scoped transaction schema;
no D39 write paths are modified. Tests cover bill/payment row preservation, not every table.

## Local verification — 2026-11-24

- Baseline: b70c36a plus T31 candidate.
- Java 17 and isolated MySQL 8.0.46 finora_test.
- Fresh Maven verify: 345 tests passed.
- T31: 13 calculation and 10 controller tests passed.
- FinanceWriteConcurrencyTest: 27 passed.
- Zero failures, errors or skips.
- JaCoCo coverage gate and application packaging passed.
- Database upgrade V8 to V13 passed.
- Maven exit code 0 at 21:21:22 Asia/Colombo.

This supersedes the pending local execution statement above.
Formal dependency closure, contract approval, Android integration,
live-device acceptance and independent review remain pending.

## Execution 49 cross-month check

A mock-based service test verifies the explicit date override without consulting recorded
income, includes unpaid occurrences in the current and next months through income day,
excludes paid and later bills, and applies declared arrears and savings once. The focused
Java 17 planning run passed 29/29 with zero failures, errors or skips. Main contains the
backend endpoint. No Android route or screen invokes it yet.

Contract gap: a sum of multiple individually valid bills can exceed the 13-integer-digit
limit on confirmedTrackedUnpaidBills. That amount can be displayed but cannot be confirmed
through the current request DTO. The published input limit and exact confirmation behavior
are unchanged. A wider aggregate or a different confirmation representation requires a
recorded contract decision. Persistent income-date and Android acceptance gates remain open.

## Execution 50 confirmation-range reproduction

Two eligible unpaid bills at LKR 5,000,000,000,000.00 each are individually valid.
The response is BILL_CONFIRMATION_REQUIRED with two bills and the exact JSON number
10000000000000.00 in trackedUnpaidBills. The Jakarta request validator accepts
9999999999999.99 and rejects the exact quoted total on confirmedTrackedUnpaidBills
under its 13-integer-digit, two-decimal, nonnegative contract. The service also applies
the same maximum. DailyGuideServiceTest passed 2/2 on Java 17.

The other submitted amounts are separate user declarations with the same magnitude
limit; only availableFunds may be negative. Derived spendableFunds and shortfall can
also aggregate beyond that magnitude but are not round-tripped into a mandatory
confirmation field. There is no Android daily-guide consumer on the checked main.
If a wider aggregate confirmation is approved, align the DTO and service validations
for that field only, preserve exact decimals and equality, and test both sides of the
old boundary and the Android decimal round trip. This change does not alter the API.
