# FINORA — API Contract

The single list of REST endpoints. Every task that adds or changes an endpoint adds or updates its row here in the same commit. In `dev` the generated OpenAPI JSON is at `/v3/api-docs`; this file is the hand-checked contract.

## Conventions
- Base path `/api/v1` (except `/actuator/health` and dev-only `/v3/api-docs`). JSON only (`Content-Type: application/json`).
- Auth: `Authorization: Bearer <access JWT>` (T05). Roles `USER`, `ADMIN`, `STAFF` (D01). **Public** = no token.
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
`fields` appears only for `VALIDATION_ERROR`.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation failed, missing/wrong-type parameter |
| 400 | `BAD_REQUEST` | Body missing or not valid JSON |
| 401 | `UNAUTHORIZED` | No token, or token invalid/expired |
| 403 | `FORBIDDEN` | Logged in but the role is not allowed |
| 404 | `NOT_FOUND` | Unknown path, or resource missing / owned by another user |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method for the path |
| 409 | `CONFLICT` | Duplicate (e.g. email already registered) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Body is not `application/json` |
| 500 | `INTERNAL_ERROR` | Unexpected server error (details only in server logs) |

Tasks may add codes; add them to this table and to `api:common/error/ErrorCodes.java`.

## Endpoints

| Method | Path | Role | Request | Response | Task |
|---|---|---|---|---|---|
| GET | `/actuator/health` | Public | — | `200 {"status":"UP"}` | T04 |
| GET | `/v3/api-docs` | Public, **dev profile only** (404 elsewhere) | — | `200` OpenAPI 3 JSON | T04 |
