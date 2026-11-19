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

**Tokens (D07):** access token = JWT HS256 signed with `JWT_SECRET`, claims `sub` = user id, `role`; valid 30 min. Refresh token = random string valid 14 days, single use; the server stores only its SHA-256 hash. Password rule (D09): 8–72 chars, at least one letter and one digit.
