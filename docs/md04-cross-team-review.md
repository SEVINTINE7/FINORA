# MD04 cross-team review handoff

D41 records Sevindu's T33 and Thilina's T30 scoped early starts. Formal dependencies and independent review remain in force. This handoff distinguishes repository implementation from device acceptance.

| Requirement and owner | Formal gates | Implementation | Evidence and remaining check |
|---|---|---|---|
| T33.1 · Sevindu · previous-month snapshot, one row per owner/month | T22 done, T23 open | Colombo scheduler, D20 and T22 reuse, V10 unique snapshot | MySQL repeated materialization, Colombo year boundary, clean and upgrade migrations. Isolated scheduler produced October totals and a null score. |
| T33.2 · Sevindu · recalculate existing snapshots after a committed transaction change | Same | Dirty flag commits with each mutation, including CSV/SMS imports through transaction service. Worker takes the D39 owner lock before reading. Old and new months, adjacent payday windows, and relevant planning/declaration inputs are invalidated. | MySQL rollback, month moves, stored score recalculation, and deterministic worker/writer contention. Android matched the fixture after a committed edit. |
| T33.3 · Sevindu · USER monthly history API | Same | Inclusive bounded range, missing months omitted, null scores, pending months disclose no stale figures | MySQL role, owner, range, decimal and nullable score checks. |
| T33.4 · Sahan · History month list and score trend | Same | More route, ranges, exact amount labels, chart gaps for missing scores, retry and session binding | Emulator matched exact totals before and after an edit. HMD Android 14 displayed LKR 5,000.00, 92/100 and returned via Back. Physical bilingual History and rotation remain open. |
| T30.1 · Thilina · USER V1 verified advice or rule fallback | T25, T29 and T38 open | Existing verification pipeline outside persistence transaction, one stable populated delivery per owner/month | Existing provider/privacy suites used mocks. MySQL delivery tests cover canonical rule fallback and stable IDs. Live providers require separately authorized credentials and environment. |
| T30.2 · Thilina · owner-only rating 1–5 once | Same | Existing DB unique advice/user rule, conditional insert under owner lock, 409 on duplicate, GET reconciliation | MySQL validation, foreign ID rejection, role checks and concurrent 201/409. |
| T30.3–T30.5 · Sahan · V1 advice, exact figures, rating and compile-time mode | Same | Insights route, canonical advice, guarded Sinhala templates for known actions and exact facts, rating guards and explicit reconciliation. Mode comes from BuildConfig. | Emulator and HMD matched LKR 50.23 in both languages. After a dropped 201 response, one POST committed and the explicit GET showed rating 5 with its original delivery ID. Native-speaker review remains open. |
| T38 · Sahan · correct tabs, all destinations and no stale data after edits | T37 done, T09 open | History under More and V1 Advice under Insights, both non-exported and USER-session-bound | Form-driven create, edit and delete refreshed Accounts, Categories, Transactions, Budgets, Bills and Goals. The transaction form created LKR 731.20, edited it to LKR 731.21 and removed the row with immediate list refresh. Goal contribution add refreshed. The full form-driven matrix, including import and contribution removal flows, and physical rotation remain open. |

## Review focus

- **Sevindu:** Confirm D27 restart/catch-up policy, the V10 schema, dirty-snapshot handling, and the ordering of the D39 user lock across finance and history work. Inspect apps and queries for historical displays during recalculation.
- **Thilina:** Confirm the first-delivery monthly lifecycle, exact canonical provider output, rating uniqueness, and GET reconciliation. Provider configuration stays server-side. Mocked provider success and fallback do not establish live acceptance.
- **Sahan:** Finish the T38 form-driven matrix and physical rotation. On HMD Android 14, Sahan confirmed both Advice month-control TalkBack labels in English and Sinhala. The disposable test profile's timeout, locale and accessibility settings were restored, the temporary API mapping was removed, and Android returned to the owner profile. Native-speaker review of the 22 new resource pairs remains pending.

## Verification

- Maven verify passed with 313 tests and the coverage gate, including 27 D39 MySQL concurrency regressions. Isolated MySQL verified both clean V1–V11 migration and V9–V11 upgrade.
- Android passed 140 JVM tests, V1 debug and unsigned release assembly, and lint with zero issues. The merged release manifest excludes READ_SMS.
- MD04 technical acceptance is 19/20. T38's final acceptance remains open. T33 is tracked separately and remains in progress with T23 open. T30 remains in progress with T25, T29 and T38 open.

## Audit findings addressed

- **HIGH** · Fractional numbers in MySQL JSON can round. A synthetic JSON query rounded 9007199254740993.21. V10/V11 write exact decimal strings inside JSON and decode them into exact public decimals; MySQL regression cases cover both routes.
- **HIGH** · An asynchronous refresh can leave stale materialized totals. Dirty months disclose only the pending state until the locked worker finishes recalculation.
- **MEDIUM** · A committed mutation can be lost to a snapshot worker or an owner change can leave old views visible. MySQL checks the owner-lock ordering and rollback. Android clears protected content before redirecting a replaced login.

Remaining review items include provider execution during concurrent first delivery, previous-month-only catch-up behavior after downtime, and the physical checks above. The stable advice key prevents competing provider results from creating multiple delivered rows.
