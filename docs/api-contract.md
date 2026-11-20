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
| 403 | `ACCOUNT_DISABLED` | Login/refresh for a disabled account, or any request with a still-valid token of a disabled account (T06) |
| 409 | `EMAIL_TAKEN` | Register with an email that already exists (case-insensitive) |
| 500 | `INTERNAL_ERROR` | Unexpected server error (details only in server logs) |

Tasks may add codes; add them to this table and to `api:common/error/ErrorCodes.java`.

## Endpoints

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
