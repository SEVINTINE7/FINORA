# FINORA — API Contract

The single list of REST endpoints. Every task that adds or changes an endpoint adds or updates its row here in the same commit. In `dev` the generated OpenAPI JSON is at `/v3/api-docs`; this file is the hand-checked contract.

## Conventions
- Base path `/api/v1` (except `/actuator/health` and dev-only `/v3/api-docs`). JSON only (`Content-Type: application/json`).
- Auth: `Authorization: Bearer <access JWT>` (T05). Roles `USER`, `ADMIN`, `STAFF` (D01). **Public** = no token.
- Role rules (T06, default deny): `/api/v1/auth/**` public · `/api/v1/admin/**` ADMIN only · `/api/v1/staff/**` STAFF only · every other `/api/v1/**` path USER only (Admin/Staff get 403 on user finance endpoints). Wrong role → 403 `FORBIDDEN`; no/invalid token → 401.
- Money: JSON strings or numbers with 2 decimals, LKR (D06), e.g. `"1250.00"`. Dates `YYYY-MM-DD`; timestamps ISO-8601 UTC, e.g. `2026-11-18T10:15:30.123Z`.
- Another user's resource → `404 NOT_FOUND` (never 403).
- No CORS (Android clients only), no cookies or sessions.

## Error JSON
Every error, including 401/403 from security and 404 for unknown paths:

```json
{
  "timestamp": "2026-11-18T10:15:30.123Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/v1/transactions",
  "fields": [ { "field": "amount", "message": "must be greater than or equal to 0.01" } ]
}
```
`fields` appears only when a request body fails validation.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation failed (with `fields`), or a URL/query parameter is missing or the wrong type (no `fields`) |
| 400 | `BAD_REQUEST` | Body missing or not valid JSON |
| 401 | `UNAUTHORIZED` | No token, or token invalid/expired |
| 403 | `FORBIDDEN` | Logged in but the role is not allowed |
| 404 | `NOT_FOUND` | Unknown path, or resource missing / owned by another user |
| 409 | `CONFLICT` | Duplicate (e.g. email already registered) |
| 400 | `UNDER_18` | Register with `over18=false` |
| 401 | `INVALID_CREDENTIALS` | Login: wrong password **or** unknown email (same body for both) |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh token unknown, expired, already used or logged out |
| 409 | `CONFLICT` | Duplicate name (account per user; category per user + kind, including defaults) (T09) |
| 409 | `IN_USE` | Deleting a category used by transactions, budgets or bills (T09) |
| 413 | `FILE_TOO_LARGE` | Uploaded CSV over 1 MB (T12) |
| 403 | `ACCOUNT_DISABLED` | Login/refresh for a disabled account, or any request with a still-valid token of a disabled account (T06) |
| 409 | `EMAIL_TAKEN` | Register with an email that already exists (case-insensitive) |
| 500 | `INTERNAL_ERROR` | Unexpected server error (details only in server logs) |

Tasks may add codes; add them to this table and to `api:common/error/ErrorCodes.java`.

## Endpoints

### Health score (T23, D20)

| Method | Path | Role | Request | Response | Task |
|---|---|---|---|---|---|
| GET | `/api/v1/score?month=YYYY-MM` | USER | required month, year 1000-9999 | `200 {month, modelVersion, status, total, scoredWeight, missingWeight, notApplicableWeight, reserveAsOf, parts[]}`; bad/missing month 400 | T23 |
| GET | `/api/v1/score-inputs/{month}` | USER | own month only; no user id accepted | `200 {month, emergencyReserve, reserveAsOf, noApplicableDebt, noApplicableGoals, confirmedAt}`; absent declaration returns null fields | T23 |
| PUT | `/api/v1/score-inputs/{month}` | USER | `{emergencyReserve?, reserveAsOf?, noApplicableDebt?, noApplicableGoals?}` | `200` same as GET; 400 invalid/contradictory declarations | T23 |

Score input PUT replaces that month's declaration. Reserve amount (nonnegative LKR, max 13 integer/2 fractional digits) and date must be supplied together or both omitted; date belongs to the month and is not future. Zero is an explicit reserve declaration, null is unknown. Booleans are nullable: only explicit `true` establishes no applicable tracked debt/goals, and cannot contradict recorded evidence. `confirmedAt` is UTC; reserve balances remain USER-DECLARED, not verified bank balances.

`parts` are ordered Spending Control / Debt Payments / Goal Progress / Savings / Emergency Savings. Each part is `{key,weight,status,points,reasonKey,facts}` with D20 weights 45/20/15/10/10. Status is `SCORED`, `NOT_APPLICABLE` or `UNAVAILABLE`; null points never count as zero. Any unavailable part makes `total=null`, otherwise normalize only applicable weights and round HALF_UP to integer. Component points are 2-decimal HALF_UP. The model is `D20-v1`; facts contain aggregate numbers, not merchants/names. Existing security gives anonymous 401 and ADMIN/STAFF 403.

Source semantics: consumption = all recorded expense minus default Savings expense; loan expenses remain inside consumption once. Debt uses current active bills linked to the default Loan payment category and monthly paid markers, not actual repayment amounts/timeliness. Loan transactions without a bill schedule cannot establish repayment performance. Goals use current non-cancelled targets (including DONE), cap contributions separately per goal, and include only contribution dates through month end. Bills/goals created after the selected month are excluded (UTC created_at converted against the Colombo month boundary). Current target/bill edits and cancellations may change recomputed historical scores; no historical snapshots or score history persistence are claimed (T33). `N` is recorded NEED expense, not an independently verified essential budget. A reserve date inside the month is displayed as supplied; it is not assumed to be a month-end balance. No score/data GET writes financial records. D20 acceptance still depends on the remaining T18/T19 work; backend-only implementation was explicitly authorized by Thilina on 2026-11-22.

### Other endpoints

| Method | Path | Role | Request | Response | Task |
|---|---|---|---|---|---|
| GET | `/actuator/health` | Public | — | `200 {"status":"UP"}` | T04 |
| GET | `/v3/api-docs` | Public, **dev profile only** (404 elsewhere) | — | `200` OpenAPI 3 JSON | T04 |
| POST | `/api/v1/auth/register` | Public | `{email, password, fullName, language: "EN"\|"SI", over18: true, consentVersion: "1.0"}` | `201 {userId}` · 400 `VALIDATION_ERROR`/`UNDER_18` · 409 `EMAIL_TAKEN` | T05 |
| POST | `/api/v1/auth/login` | Public | `{email, password}` | `200 {accessToken, refreshToken, expiresIn: 1800, role, language}` · 401 `INVALID_CREDENTIALS` · 403 `ACCOUNT_DISABLED` | T05 |
| POST | `/api/v1/auth/refresh` | Public | `{refreshToken}` | `200` same as login (new pair; old refresh token revoked) · 401 `INVALID_REFRESH_TOKEN` | T05 |
| POST | `/api/v1/auth/logout` | Public | `{refreshToken}` | `204` (always, even if the token was unknown) | T05 |
| GET | `/api/v1/me` | USER | — | `200 {email, fullName, language, consentVersion}` (`consentVersion` = `null` after withdrawal, D15) | T07 |
| PUT | `/api/v1/me` | USER | `{fullName, language: "EN"\|"SI"}` | `200` same as GET · 400 `VALIDATION_ERROR` | T07 |
| POST | `/api/v1/me/consent/withdraw` | USER | — | `204`; sets `withdrawn_at`, revokes all the user's refresh tokens (logged out on every device once the access token expires) | T07 |
| GET | `/api/v1/accounts` | USER | — | `200 [{id, name, type, openingBalance, archived}]` (own only, incl. archived) | T09 |
| GET | `/api/v1/accounts/{id}` | USER | — | `200 {id, name, type, openingBalance, archived}` · 404 (missing or another user's) | T09 |
| POST | `/api/v1/accounts` | USER | `{name, type: "BANK"\|"CASH"\|"CARD"\|"WALLET", openingBalance: "15000.00"}` | `201` account · 400 · 409 `CONFLICT` (name taken) | T09 |
| PUT | `/api/v1/accounts/{id}` | USER | same as POST | `200` account · 400 · 404 · 409 `CONFLICT` | T09 |
| DELETE | `/api/v1/accounts/{id}` | USER | — | `204`; if transactions use it the account is only archived (`archived: true`) · 404 | T09 |
| GET | `/api/v1/categories` | USER | — | `200 [{id, name, nameSi, kind, needOrWant, isDefault}]` = active defaults + own, sorted by kind, name | T09 |
| GET | `/api/v1/categories/{id}` | USER | — | `200` category · 404 (missing or another user's) | T09 |
| POST | `/api/v1/categories` | USER | `{name, nameSi?, kind: "INCOME"\|"EXPENSE", needOrWant?: "NEED"\|"WANT"}` | `201` category · 400 · 409 `CONFLICT` | T09 |
| PUT | `/api/v1/categories/{id}` | USER | `{name, nameSi?, needOrWant?}` (kind cannot change) | `200` category · 403 `FORBIDDEN` (default) · 404 · 409 `CONFLICT` | T09 |
| DELETE | `/api/v1/categories/{id}` | USER | — | `204` · 403 `FORBIDDEN` (default) · 404 · 409 `IN_USE` | T09 |
| GET | `/api/v1/transactions` | USER | query (all optional): `from`, `to` (YYYY-MM-DD), `type` INCOME\|EXPENSE, `categoryId`, `accountId`, `page` (0-based, default 0), `size` (default 20, max 100) | `200 {items: [transaction], page, size, totalItems, totalPages}`, newest `txnDate` first · 400 bad filter | T10 |
| GET | `/api/v1/transactions/summary` | USER | query `from`, `to` (both required, inclusive) | `200 {from, to, income, expense, net}` (2 decimals, `net` may be negative) · 400 | T10 |
| GET | `/api/v1/transactions/{id}` | USER | — | `200 {id, accountId, categoryId, type, amount, txnDate, merchant, note, source}` · 404 | T10 |
| POST | `/api/v1/transactions` | USER | `{accountId, categoryId?, type, amount: "1250.00" (≥ 0.01, 2 dp), txnDate, merchant?, note?, source?: MANUAL\|SMS\|PASTE\|CSV (default MANUAL)}` | `201` transaction · 400 (validation; account not own/archived; category not visible or kind ≠ type) | T10 |
| PUT | `/api/v1/transactions/{id}` | USER | same as POST | `200` transaction · 400 · 404 | T10 |
| DELETE | `/api/v1/transactions/{id}` | USER | — | `204` · 404 | T10 |
| POST | `/api/v1/imports/csv/preview` | USER | `multipart/form-data`, part `file` = CSV per `docs/csv-template.md` (≤ 1 MB, ≤ 2000 rows) | `200 {totalRows, validRows, errorRows, duplicateRows, rows: [{line, txnDate, type, amount, accountId, accountName, categoryId, categoryName, merchant, note, occurrence, duplicate, errors: [string]}]}`; nothing saved · 400 (empty, not UTF-8, bad header, broken quoting, > 2000 rows) · 413 `FILE_TOO_LARGE` | T12 |
| POST | `/api/v1/imports/csv/confirm` | USER | `{rows: [{line, txnDate, type, amount, accountId, categoryId?, merchant?, note?, occurrence}]}` (1–2000; the valid preview rows the user picked) | `200 {saved, skippedDuplicateLines: [line]}`; saved with `source=CSV` and `dedup_hash` (D25); rows already imported are skipped · 400 (any row invalid → nothing saved, message starts `Line N:`) · 409 `CONFLICT` (same rows confirmed twice at once) | T12 |
| GET | `/api/v1/budgets?month=YYYY-MM` | USER | `month` required | `200 [budget]`, budget = `{id, categoryId, month, limitAmount, used, remaining, state: "OK"\|"NEAR"\|"OVER"}` · 400 bad/missing month | T17 |
| GET | `/api/v1/budgets/{id}` | USER | — | `200` budget · 404 | T17 |
| POST | `/api/v1/budgets` | USER | `{categoryId (EXPENSE, default or own), month: "2026-11", limitAmount: "20000.00"}` | `201` budget · 400 · 409 `CONFLICT` (already a budget for that category + month) | T17 |
| PUT | `/api/v1/budgets/{id}` | USER | same as POST | `200` budget · 400 · 404 · 409 `CONFLICT` | T17 |
| DELETE | `/api/v1/budgets/{id}` | USER | — | `204` · 404 | T17 |
| GET | `/api/v1/bills?month=YYYY-MM` | USER | `month` required | `200 [{id, name, amount, dueDay, categoryId, month, dueDate, paid}]`, earliest `dueDay` first · 400 | T18 |
| GET | `/api/v1/bills/summary?month=YYYY-MM` | USER | `month` required | `200 {month, total, paid, unpaid}` (total = all active bills; 2 decimals) · 400 | T18 |
| GET | `/api/v1/bills/{id}` | USER | — | `200 {id, name, amount, dueDay, categoryId}` · 404 | T18 |
| POST | `/api/v1/bills` | USER | `{name, amount: "4500.00", dueDay: 1–31, categoryId? (EXPENSE, default or own)}` | `201` bill · 400 | T18 |
| PUT | `/api/v1/bills/{id}` | USER | same as POST | `200` bill · 400 · 404 | T18 |
| DELETE | `/api/v1/bills/{id}` | USER | — | `204` (paid markers removed too) · 404 | T18 |
| POST | `/api/v1/bills/{id}/paid?month=YYYY-MM` | USER | — | `204` · 404 · 409 `CONFLICT` (already paid that month) | T18 |
| DELETE | `/api/v1/bills/{id}/paid?month=YYYY-MM` | USER | — | `204` (also when it was not paid) · 404 | T18 |
| GET | `/api/v1/goals` | USER | — | `200 [goal]`, oldest first. goal = `{id, name, targetAmount, targetDate, status, saved, remaining, progressPercent}` | T19 |
| GET | `/api/v1/goals/{id}` | USER | — | `200` goal · 404 | T19 |
| POST | `/api/v1/goals` | USER | `{name, targetAmount: "250000.00" (> 0), targetDate? (after today)}` | `201` goal · 400 | T19 |
| PUT | `/api/v1/goals/{id}` | USER | same as POST + `cancelled?: true` | `200` goal · 400 · 404 | T19 |
| DELETE | `/api/v1/goals/{id}` | USER | — | `204` (contributions removed too) · 404 | T19 |
| GET | `/api/v1/goals/{id}/contributions` | USER | — | `200 [{id, goalId, amount, date}]`, newest date first · 404 | T19 |
| POST | `/api/v1/goals/{id}/contributions` | USER | `{amount: "1000.00" (> 0), date (today or earlier)}` | `201` goal with new progress · 400 · 404 | T19 |
| DELETE | `/api/v1/goals/{id}/contributions/{cid}` | USER | — | `200` goal with new progress · 404 | T19 |
| GET | `/api/v1/dashboard?month=YYYY-MM` | USER | `month` optional (default: current month in Asia/Colombo) | `200 {month, income, expense, net, byCategory: [{categoryId, name, nameSi, total}], trend: [{month, income, expense, net}]}` · 400 | T20 |
| GET | `/api/v1/insights?month=YYYY-MM` | USER | `month` required | `200 [{id, type, month, from, to, facts{...numbers}}]` (empty list = no patterns) · 400 | T22 |

**Insights (T22, D10, D19):** recalculated on every GET (old rows for that month replaced). Types and facts:
`HIGH_WANTS` wants > 40% of income `{wants, wantsCount, income, wantsRatio, threshold}` · `HIGH_FIXED_BILLS` bills > 50% of income `{fixedBills, billCount, income, fixedBillsRatio, threshold}` · `PAYDAY_SPENDING` payday spend > 30% of expense `{paydaySpend, paydayCount, expense, paydayShare, threshold}` · `WEEKEND_SPENDING` weekend-day average > 1.5 × weekday average `{weekendSpend, weekendCount, weekendDays, weekendDailyAverage, weekdaySpend, weekdayDays, weekdayDailyAverage, threshold}`. Exactly at a threshold = no insight; no income = no income-based insights. The app builds the text from `type` + `facts`.

**Dashboard (T20, D17):** totals use the user's transactions from the 1st to the last day of `month`. `byCategory` = EXPENSE only, biggest first; records without a category are one item with `categoryId`/`name`/`nameSi` null. `trend` = 6 months ending with `month`, oldest first; empty months are 0.00. All amounts have 2 decimals.

**Goals (T19, D16):** `saved` = sum of the goal's contributions (can be more than the target). `remaining` = target − saved, never below 0.00. `progressPercent` = saved × 100 / target, rounded down, max 100. `status` is set by the server: `DONE` when saved ≥ target, else `ACTIVE`; `CANCELLED` only via PUT `cancelled: true` (a PUT without it un-cancels). Contributions are not transactions and cannot be edited (delete and add again).

**Bills (T18):** paid is stored per month, so every new month starts unpaid. `dueDate` = `dueDay` in that month, or the last day if the month is shorter (due day 31 → 28/29 Feb, 30 Apr).

**Budgets (T17):** `used` = sum of the user's EXPENSE transactions in that category from the 1st to the last day of `month`. `remaining` = `limitAmount − used` (negative when over). `state`: `OVER` if used > limit, `NEAR` if used ≥ 80% of limit, else `OK`.

**Tokens (D07):** access token = JWT HS256 signed with `JWT_SECRET`, claims `sub` = user id, `role`; valid 30 min. Refresh token = random string valid 14 days, single use; the server stores only its SHA-256 hash. Password rule (D09): 8–72 chars, at least one letter and one digit.


## SMS format distribution (T45.3)

| Method | Path | Role | Response |
|---|---|---|---|
| GET | `/api/v1/sms-formats` | USER | `200` JSON, `Cache-Control: no-store` |

No query parameters or request body. The complete schema-v1 snapshot has exactly two keys, `schemaVersion` (integer `1`) and `templates` (array). A populated v1 snapshot contains the three approved D35 NSB templates. Each template has `id`, `version`, `senderIds`, `numberStyle`, `dateSource`, `datePattern`, `markers` and `fields`. NSB v1 uses `COMMA_GROUPED`, `MESSAGE` and `DAY_MONTH_YEAR_WITH_TIME`. Marker keys are `prefix`, `debit`, `credit`, `suffix` and `requiredDirection`. The ordered field entries have `name` and a literal `delimiter`. The bundled Android asset contains the complete approved example. No executable patterns, message examples or user data are returned.

A successful `{"schemaVersion":1,"templates":[]}` disables this bundle, including offline use after a client has observed the empty response. Only a validated, populated response re-enables it. An unavailable endpoint, network failure or invalid response does not change the last known disablement. Before any observed disablement, the Android client can use the approved bundled NSB snapshot offline. Disablement is installation-wide for the configured API origin because the response is shared reference data, with updates accepted only from the current USER login.

Missing or invalid credentials use the standard `401` response, and ADMIN/STAFF receive `403`. An active D35 row that fails the pinned approval check yields `503 SMS_FORMATS_UNAVAILABLE` with the standard error body and no format payload.


## SMS transaction batch (T15.3, D26/D38)

| Method | Path | Role | Success |
|---|---|---|---|
| POST | `/api/v1/transactions/batch` | USER | `200 {"items":[{"index":1,"status":"SAVED","possibleManualMatch":[]}]}` |

Request `{"items":[{"dedupHash":"<64 lowercase SHA-256 hex>","transaction":{"accountId":1,"categoryId":null,"type":"EXPENSE","amount":1250.75,"txnDate":"2026-11-21","merchant":"TEST SHOP","source":"SMS"}}]}`. Between 1 and 200 rows. Source is SMS or PASTE. Merchant and same-kind category are optional; note is not accepted. No SMS text or sender metadata.

Validates the complete batch, including duplicates, before saving. A semantically invalid item returns standard 400 BAD_REQUEST with a one-based `Item N: ...` message and saves nothing. Invalid JSON or envelope has the standard 400 response; 401/403 follow the normal role rules. Accounts must belong to the caller and be active; categories must be visible to that user. Dates use MySQL's supported range (1000–9999). Amounts are exact LKR decimals.

Each result corresponds to its one-based request index. `DUPLICATE` is an existing or repeated fingerprint for the same user; the item is unsaved and requires review under D38. `SAVED` can include IDs in `possibleManualMatch` for the caller's preserved MANUAL rows matching account, date, type and amount. Retry only after user confirmation with the same frozen request. The unique key applies while the matching transaction exists.

## D41 monthly history and V1 advice

- `GET /api/v1/history?from=YYYY-MM&to=YYYY-MM` is USER-only. Both bounds are inclusive and required, with at most 24 months and years 1000–9999. Returns oldest-first stored snapshots `[{month,income,expense,net,score,insights:[{type,facts}],refreshPending}]`; monetary fields are exact JSON decimals. Missing snapshots are omitted, and an available snapshot can have a null score. Invalid bounds return 400. D27 recalculation is asynchronous after commit. A dirty snapshot returns `refreshPending: true`, null totals and score, and empty insights until refresh succeeds. Bill changes, category reclassifications, related goal and contribution changes, and score-input changes invalidate existing snapshots under the same write order. Budgets are not inputs to the current D20 score or T22 patterns.
- D43 adds nullable `reconstructedAt` to each History row. A non-null ISO-8601 UTC timestamp identifies recovery from the committed records available at first reconstruction. Values can differ from what was recorded at month-end. The timestamp and provenance survive dirty refreshes. Rows created by the ordinary D27 workflow have null provenance, including rows created before V13.
- `GET /api/v1/advice?month=YYYY-MM&mode=V1` is USER-only. Both parameters are required. A delivered month returns `{month,id,source,verified,items,rating}` where items follow the existing AdviceItem schema. First verified AI or canonical rule delivery is frozen per user and month. If a first delivery is already being generated, return the stored result if available or `503 ADVICE_PENDING` so the client can retry. A short database transaction claims generation before provider calls. The claim expires after 120 seconds and cannot publish after expiry. If no action is eligible, return an empty list and null ID and rating. The empty state is not frozen. `rating` is null until submitted.
- `POST /api/v1/advice/{id}/rating` is USER-only. It takes `{"rating": integer}` from 1–5 and returns 201 with `{rating}`. Invalid IDs or inputs return 400, missing or foreign delivery returns 404, and duplicate ratings return 409. Reconcile a lost response with the owner-scoped monthly GET and never automatically replay the write.

## Staff queue statistics (T46.1)

`GET /api/v1/staff/stats` is STAFF-only and returns `200 {myOpenTickets,unassignedTickets}`. Both fields are nonnegative counts of tickets in OPEN or IN_PROGRESS state. The first count is limited to the authenticated assignee, and the second includes only tickets with no assignee. The response contains no ticket contents, user identifiers or financial fields.

## SMS diagnostics (T49)

`POST /api/v1/support/sms-diagnostics` is USER-only and takes exactly `{bankCode,formatVersion,errorCode}`. Unknown bank or version is explicitly JSON null. Only D35's NSB v1 is recognized. A version requires a bank. The four error codes are defined by D47. Success returns 204, including repeat submissions by the same user for the same tuple during a UTC day. Raw messages, amounts, account information and additional keys are rejected by the shape and value allowlist. `GET /api/v1/staff/sms-diagnostics` returns aggregate rows `{bankCode,errorCode,formatVersion,reports}` for STAFF only, without user identifiers.

STAFF can flag a known group with `POST /api/v1/staff/sms-diagnostics/escalations` and `{bankCode,errorCode}`. It returns 204 and deduplicates existing open or acknowledged escalations. ADMIN can read active flags with `GET /api/v1/admin/sms-diagnostics/escalations`, returning up to 100 rows with `{id,bankCode,errorCode,status,createdAt}`. ADMIN can transition a flag with `PATCH /api/v1/admin/sms-diagnostics/escalations/{id}` and `{status}`. The sequence is OPEN → ACKNOWLEDGED → CLOSED. Stale transitions return 409. Neither endpoint carries free text or customer data.

## Support requests and saved replies (T47/T48)

USER `POST /api/v1/support/tickets` accepts one of four canonical subject/body pairs for sign-in, navigation, SMS permission or language. Optional `error_code` is null or the matching topic code, respectively `SIGN_IN_FAILED`, `NAVIGATION_HELP`, `SMS_PERMISSION_DENIED`, or `LANGUAGE_UPDATE_FAILED`. The ticket owner and assigned STAFF can see the code. It is hidden in the claim queue. Returns 201 with an ID. D52 also permits the server to discard one trailing identifier-only line before persistence. Its exact prefix must be a newline followed by `Account number: ` or `Card number: `, followed by 8–19 or 13–19 ASCII digits respectively, with optional single spaces or hyphens between digits. The Android form sends the canonical body and does not solicit identifiers. Free text, attachments, credentials, SMS text, extra fields and malformed identifier lines are rejected. SMS troubleshooting metadata goes through the separate T49 channel. USER can list only their own tickets and read their stored replies at `GET /api/v1/support/tickets/{id}/replies`.

STAFF lists the queue with `GET /api/v1/staff/tickets`. Unassigned rows disclose only the ID and status. `POST /api/v1/staff/tickets/{id}/claim` takes an OPEN unassigned ticket to IN_PROGRESS in one update. Only the assignee can close via `POST …/{id}/close` or reply via `POST …/{id}/replies` with `{revision,language}`. The server renders an approved revision and stores the exact sent text. The reply is preserved if its source revision is retired.

STAFF `GET /api/v1/staff/reply-catalog` returns draft, active and retired versions available for review. `POST` with `{topic}` creates a draft using server-owned bilingual sentence blocks. A different STAFF member approves a draft via `POST …/{id}/approve`. STAFF can retire a revision via `POST …/{id}/retire`. A database unique key permits one active revision per topic. No user-provided text or interpolation is accepted. Separate native-speaker approval remains a release gate.

## Staff provisioning (T43)

ADMIN can list or create STAFF at `/api/v1/admin/staff`. Creation accepts `{email,fullName}` and returns 201 with `{id,temporaryPassword}` and `Cache-Control: no-store`. The role is fixed to STAFF. A pending account has no active sign-in. The temporary password itself is a single-use password-change credential, valid for 24 hours and accepted only at `POST /api/v1/auth/staff/activate` with `{email,temporaryPassword,password}`. A successful activation returns 204 without tokens. Sign in again with the new password.

ADMIN can replace a pending temporary password at `POST /api/v1/admin/staff/{id}/activation`, which invalidates the previous temporary password. ADMIN can disable or re-enable activated STAFF at `PATCH /api/v1/admin/staff/{id}` with `{enabled}`. Each transition advances a server-side session generation, so prior access and refresh credentials remain invalid after re-enable. No endpoint promotes a user to ADMIN.

ADMIN can activate or disable the hash-pinned D35 NSB bundle via `PATCH /api/v1/admin/sms-formats/NSB/1` with `{active}`. Activation verifies the approved content under a row lock. It returns 204 and preserves D36 distribution behavior. Draft format authoring and rollback remain outside this endpoint.

ADMIN `GET /api/v1/admin/stats` returns `{activeUsers,disabledUsers,staff,openTickets,activeFormats}`. Counts include USER accounts by status, all STAFF accounts, OPEN and IN_PROGRESS tickets, and active bank bundles. No customer data or financial fields are returned.

ADMIN `GET /api/v1/admin/sms-formats/NSB/revisions` lists `{version,published,active}` for the last 100 deployment revisions. `POST …/revisions/{revision}` drafts a byte-identical copy of the approved NSB bundle and returns 201. `DELETE …/revisions/{revision}` discards an unpublished inactive draft and returns 204, or 404 for a missing or protected revision. `POST …/{revision}/publish` validates the pinned content and publishes the draft permanently. `POST …/{revision}/activate` atomically switches to any published version, including an older one, and `POST …/{revision}/deactivate` disables that revision. Each transition returns 204. Deployment revision numbers do not change the template versions on the D36 wire. No message or custom format body is accepted. New parsing rules require separate approved definitions and synthetic fixture evidence.
