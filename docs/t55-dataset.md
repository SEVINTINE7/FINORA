# T55 v1 synthetic twelve-month dataset

2026-11-22. Owner: Thilina. This is invented LKR test data, not Sri Lankan bank records, a household survey,
SMS samples or evidence of financial outcomes. Amounts are plausible scenario choices, not population estimates.
No score thresholds, forecasts, payment-verification claims or new application features are introduced.

## Contents and independent totals

Period: **2025-12-01 through 2026-11-30**, including the calendar-year boundary and February's 28 days.
Blueprint: `api-test:support/EvaluationDataset.java`; explicit loader: `EvaluationDatasetSeeder.java`.

| Profile | Transactions | Income LKR | Expense LKR | Recorded net LKR |
|---|---:|---:|---:|---:|
| worker | 424 | 720000.00 | 506099.99 | 213900.01 |
| irregular | 402 | 235250.00 | 117099.99 | 118150.01 |
| Total | 826 | 955250.00 | 623199.98 | 332050.02 |

- Worker: Salary 60000/month; daily Food 500 except December's 100/day; Rent 15000 and Utilities 2000/month.
  Shopping is 0, 4000, 3999.99, then normally 5000; June is 50000. Savings allocation is December 1000,
  otherwise 3000. Annual expense breakdown: Food 170100 + Rent 180000 + Utilities 24000 + Shopping 97999.99
  + Savings 34000 = 506099.99. December is low spending; June overspends recorded income.
- Irregular: two recorded incomes in each populated month (Part-time 10000+500*i, Allowance 7500+250*i,
  i=0..11); September has no transactions. Food 200/day in populated months, Transport 2000 and
  Mobile/Internet 1000/month. Shopping cycles 500/1000/1500, Savings 500. April includes one uncategorized
  99.99 expense. Expense breakdown: Food 67000 + Transport 22000 + Mobile/Internet 11000 + Shopping 11500
  + Savings 5500 + unclassified 99.99 = 117099.99. These are fixture-generation rules, not advice formulas.
- Uses existing active V6 default categories, resolved by kind/name; no category or shared reference data is changed.
- 2 USER identities, 2 zero-opening-balance CASH accounts; 24 Shopping budgets (worker 5000, irregular 1000).
  Existing budget states cover OK, exactly 80%, just below 80%, fully used and OVER.
- 2 recurring bills (Utilities 2000; Mobile/Internet 1000), due day 31; 18 paid markers. Markers are proxies only.
- 2 goals: worker target 24000 with twelve 2000 contributions (DONE); irregular target 10000 with six 500
  contributions (ACTIVE, 30%). 18 contribution rows. Goals have no target date, an existing supported optional field.
- 21 monthly score declarations: worker 12; irregular 9, omitting December/April/August. Irregular May declares
  no reserve amount/date; worker December explicitly declares zero. Other reserve values are explicitly invented
  declarations, not inferred balances or sums of savings/contributions. No applicable debt is declared; bills are non-loan.

Only positive transaction amounts are inserted: zero transaction amounts are unsupported. Zero-income/spending
edge cases are zero aggregates for the missing-record month, not proof of no real income/spending. Missing category,
merchant/note, goal dates, reserve fields and whole declarations exercise supported nullable inputs.
Savings expenses already belong to expense; contributions, bills and reserve declarations never add duplicate transactions.
The fixed window is synthetic, including the remainder of November; it is not a report of activity that has already occurred.

## Safe reproduction

Use the existing Java 17 environment script and offline Maven cache. From `api`, with TEST_DB_* and datasource
set to the isolated loopback finora_test database, run:

```powershell
. 'C:\Users\thili\AppData\Local\Temp\finora-t50-tools\Set-TestEnvironment.ps1'
.\mvnw.cmd -B -o '-Dmaven.repo.local=C:\Users\thili\AppData\Local\Temp\finora-t50-tools\m2\repository' '-Dspring.datasource.url=jdbc:mysql://127.0.0.1:3307/finora_test' '-Dfinora.llm.enabled=false' '-Dtest=EvaluationDatasetTest,EvaluationDatasetIntegrationTest' test
```

The helper is **test-source only**: no Flyway migration, startup hook, production bean, external service or CLI.
Before any insert it requires a transaction with auto-commit off, actual catalog finora_test, and a JDBC URL on
127.0.0.1 or localhost with that exact schema. Remote hosts and other schemas are refused. Existing fixed fixture
emails cause refusal, not cleanup/upsert. Required categories are resolved before the first insert. No delete/update
of existing financial/reference data is performed. IntegrationTest rolls back; AfterTransaction asserts fixture users
are absent. MySQL auto-increment counters may advance despite rollback; row content is what is preserved.

This intentionally leaves **no persistent dataset for a phone to log into**. Fixture users have non-login test hashes;
tests use the existing JWT helper. Future device runs need a separately reviewed explicit local authentication/persistence
adapter, retaining these guards; do not enable commits or copy the seed into application migrations. No production
database was contacted to prove safety: refusal paths are mocked, and real insertion tests use only finora_test.

Deterministic transaction SHA-256 (canonical profile/date/type/category/amount order):
`14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944`.
Generated primary keys, user hashes and default audit timestamps are excluded from this content digest.
The database round-trip is compared to every blueprint row; hand totals are independent fixed expectations.

## Persistent evaluation variant

Execution 37 populated the dedicated MySQL 8.0.46 schema finora_t55_perf at 127.0.0.1:3317. The
listener and server variables confirmed loopback binding, UUID d236a029-e8bf-11f1-b816-00155de2fc9b,
and a new data directory under local application data in Finora-T55-Evaluation/data. The API account
has schema-scoped migration and runtime grants. The separate loader account has SELECT and INSERT
on that schema. Sevindu owns lifecycle approval. Thilina operates the evaluation instance and
retains the local secrets in a Windows user-protected credential file outside the worktree.

For an authorized **empty** evaluation schema:

1. Verify the isolated listener, schema, UUID, data directory and scoped accounts through the local
   provisioning record. Confirm nothing is using the intended API port and that no personal or
   production records are present.
2. Export the independently verified FINORA_T55_DB_PORT, FINORA_T55_DB_UUID and FINORA_T55_DB_DATADIR,
   preserving the trailing separator reported by MySQL. Inject FINORA_T55_API_DB_PASSWORD,
   FINORA_T55_LOADER_PASSWORD, FINORA_T55_JWT_SECRET, FINORA_T55_WORKER_PASSWORD and
   FINORA_T55_IRREGULAR_PASSWORD from local secret storage into the Java process environment.
   Clear provider credentials and inherited Spring overrides. Keep secrets out of command arguments,
   transcripts and Git.
3. Use Java 17 and offline Maven from the API directory. Select PersistentEvaluationDatasetTest and
   set the system property finora.t55.persist=true to explicitly enable the manual load. The harness
   preflights the instance before starting the API on an ephemeral IPv4 loopback port. It disables
   provider construction and refuses non-loopback requests through Java's HTTP proxy selector.
   The selector covers the Java HTTP transport used by the API, with no host-wide firewall change.
   Existing Flyway migrations apply normally. The harness uses the normal registration, consent
   and login routes, and closes the temporary API on exit.
4. The loader checks the two registered synthetic owners, current consent, empty financial state and
   shared categories. A MySQL session lock serializes loads through transaction completion. It
   inserts all financial rows in one explicit JDBC transaction and verifies content, ownership,
   counts and totals before committing. A repeat refuses any existing financial rows.
   On failure, inspect the existing environment. Preserve records and avoid resets or retries
   against any other database.

### Verified result

- The persistent run passed 5 tests with 0 failures, errors or skips, including the two original
  blueprint/guard tests. The unchanged rollback integration class passed 2 more tests against the
  separately verified finora_test instance. Maven Surefire reports retain the command results.
- The harness proved wrong endpoint/catalog/instance and owner refusal, plus missing-reference
  refusal using a simulated empty category query. It injected an extra financial row within the
  load transaction, observed the totals assertion refuse the load, and confirmed all eight fixture
  tables were empty afterward. The next load succeeded and a repeat refused.
- The normal API registered both accounts and issued normal login sessions. Both users could read
  their own November dashboard and goal. Requests in both directions for the other user's goal
  returned HTTP 404. The API was bound to 127.0.0.1:52832 for this run and stopped at test completion.
- A new MySQL connection measured 826 transactions, 24 budgets, 2 bills, 18 paid markers, 2 goals,
  18 contributions and 21 score declarations. Both profiles matched the totals above. The stored
  transaction content reproduced the documented SHA-256 from UTF-8 canonical lines, ordered by
  blueprint profile and insertion order, with LF separators and a trailing LF.
- The populated schema is retained for emulator integration. Relaunch the provider-disabled API
  against this schema on laptop loopback port 8080, verify the approved emulator and acceptance
  package, then scope adb reverse tcp:8080 tcp:8080 to that emulator serial. Sign in using the
  isolated harness and existing synthetic users. The loader is unnecessary for that step.
  Device performance and formal T55 acceptance remain open.

## Evaluation use and pending acceptance

Supports repeatable backend correctness checks, owner-isolated dashboard/budget/goal queries, month boundaries and
later query-load experiments. Four tests cover blueprint/totals, refusal-before-write guards, database/plan consistency,
owner isolation, unchanged control/reference rows, duplicate refusal and actual rollback. This small 826-row dataset
is a defined load case, not a scalability benchmark or SMS parser accuracy corpus.

Real screen performance, SMS accuracy and SUS remain unmeasured. The companion protocol is in still-open
[PR #11](https://github.com/SEVINTINE7/FINORA/pull/11) (`docs/evaluation.md`); do not duplicate it here.

| Remaining task | Owner / integration requirement |
|---|---|
| T30 | Sahan lead, Thilina support; T25/T29/T38 acceptance and advice delivery/ratings contract, with Sevindu for API decisions. No advice HTTP route claimed by this seed. |
| T31 | Thilina + Sahan; Sevindu-approved balance/next-income/day-count/earmarking policy and T17/T18/T19 integration. |
| T32 | Thilina + Sahan; baseline, category-cut units/overlap and finish-date rounding approval; T19 integration. |
| T52 | Thilina + Sevindu; PR #10 review plus real T41/T46 ADMIN/STAFF endpoint matrix. |
| T53 | Thilina + Sevindu; T56 HTTPS/proxy/log review and approved comprehensive scans; Sahan/T14 device SMS capture. |
| T54 | Thilina; PR #11 review, final T29 verifier policy and explicit authorization for actual LLM evaluation. |
| T55 | Thilina + Sahan; T16/T38, real mid-range phone, approved measurement statistic and consented usability study; D03/Beta protocol clarification. |

No task is marked complete by creation of this dataset. Final command evidence is recorded in the T55 tracker.
