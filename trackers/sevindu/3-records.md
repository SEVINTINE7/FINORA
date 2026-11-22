# Sevindu 3 — Records and CSV import (T09, T10, T12)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand exact fields/endpoints/tests (from `docs/db-schema.md`) before coding.

---

### T09 · Accounts and categories · `FR02`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T03, T06, T37 · **Touches:** `api:account/`, `api:category/`, `and:ui/accounts/`, `and:ui/categories/`
- [x] T09.1 [API] CRUD `/api/v1/accounts` (owner only; archive instead of delete if it has transactions)
- [x] T09.2 [API] `/api/v1/categories`: list = defaults + own; CRUD on own only; defaults read-only
- [x] T09.3 [API] Duplicate name per user → 409; delete category in use → 409 `IN_USE`
- [ ] T09.4 [AND·Sahan] Accounts list + add/edit screen
  - List owned accounts including archived rows, with name, type and exact LKR opening balance. Add and edit name (required, 60 characters), account type and exact signed opening balance (13 integer digits, 2 fractional). Use USER-only GET/POST `/api/v1/accounts` and GET/PUT/DELETE `/api/v1/accounts/{id}`. Confirm removal, then reload after 204 to distinguish archive from deletion. Verify money precision, conflicts, stale-login cancellation, rotation, offline recovery and local API CRUD.
- [ ] T09.5 [AND·Sahan] Categories list grouped income/expense, add/edit own
  - Group active defaults and owned rows by income and expense. Default rows are read-only. Create with required name and kind, and optional Sinhala name and Need/Want. Edit sends name and optional fields, preserving unchanged values and the server's immutable kind. Use USER-only GET/POST `/api/v1/categories` and GET/PUT/DELETE `/api/v1/categories/{id}`. Verify grouping, Sinhala fallback, default guards, duplicate and IN_USE errors, session isolation and local API CRUD.

**Done when**
- [x] Tests: CRUD, duplicate → 409, in-use delete → 409, user B cannot see user A's (404), cannot edit a default category
- [ ] Screens create/edit/delete against local API

**Handoff notes:**
- API part done (T09.1–T09.3); **T09.4–T09.5 (Android, Sahan) and the screens check still open — blocked by T37**.
- `api:account/` (`Account`, `AccountType`, repo, service, controller, `dto/`) and `api:category/` (`Category`, `CategoryKind`, `NeedOrWant`, …). New error code `IN_USE`.
- Account DELETE always 204: archived if transactions use it (native count query until T10 adds `Transaction`). Accounts list includes archived ones — T10/T11 should refuse/hide archived accounts for new entries.
- Category names must be unique per user + kind **including defaults** (can't make own "Food" expense). Defaults: read OK, PUT/DELETE → 403 `FORBIDDEN`. PUT cannot change `kind`. "In use" = transactions, budgets or bills.
- Tests: `AccountControllerTest` (5), `CategoryControllerTest` (7); suite 67/67. `docs/api-contract.md` updated.


---

### T10 · Income and expense records · `FR02`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T09 · **Touches:** `api:transaction/`, `and:ui/transactions/`
- [x] T10.1 [API] CRUD `/api/v1/transactions`; account and category must belong to user (or be default)
- [x] T10.2 [API] List with filters `from`, `to`, `type`, `categoryId`, `accountId`, paging; sorted by `txn_date` desc
- [x] T10.3 [API] `GET /api/v1/transactions/summary?from&to` → totals income/expense/net (BigDecimal)
- [ ] T10.4 [AND·Sahan] Transaction list with filters + edit/delete

**Done when**
- [x] Tests: totals exact to the cent after create/edit/delete; filters; other user's account/category rejected; isolation 404

**Handoff notes:**
- API part done (T10.1–T10.3); **T10.4 (Android, Sahan) still open — blocked by T37**.
- `api:transaction/`: `Transaction`, `TransactionType`, `TransactionSource`, repo (`search` = one JPQL query with optional filters, `sumAmount`), service, controller, `dto/` (incl. `TransactionPage`, `SummaryResponse`).
- Another user's account/category in the body → **400** `VALIDATION_ERROR` ("Account not found"/"Category not found"), not 404 — 404 is only for the transaction in the URL. Category kind must equal type. Archived account: no new records, existing ones still editable.
- Summary sums in MySQL (DECIMAL), returned with scale 2; `from` > `to` → 400. `dedup_hash` not mapped yet (T15). `AccountRepository.countTransactions` is still a native query — fine to leave.
- Tests: `TransactionControllerTest` (11); suite 78/78. `docs/api-contract.md` updated.


---

### T12 · CSV import · `FR03`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T10 · **Touches:** `api:importer/`, `and:ui/importer/`, `docs/csv-template.md`
- [ ] T12.1 [API] Define template `date,type,amount,category,account,merchant,note` (date `YYYY-MM-DD`) in `docs/csv-template.md`
- [ ] T12.2 [API] `POST /api/v1/imports/csv/preview` → parsed rows + per-row errors + duplicate flags; nothing saved
- [ ] T12.3 [API] `POST /api/v1/imports/csv/confirm` saves selected rows (source=CSV, dedup_hash)
- [ ] T12.4 [API] Limits: ≤1 MB, ≤2000 rows; reject cells starting with `= + - @` (formula injection)
- [ ] T12.5 [AND·Sahan] Pick file → preview list → confirm

**Done when**
- [ ] Tests: valid file, bad dates/amounts, oversize, formula cells, re-import flags duplicates

**Handoff notes:** —
