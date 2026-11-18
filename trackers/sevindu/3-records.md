# Sevindu 3 — Records and CSV import (T09, T10, T12)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand exact fields/endpoints/tests (from `docs/db-schema.md`) before coding.

---

### T09 · Accounts and categories · `FR02`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T03, T06, T37 · **Touches:** `api:account/`, `api:category/`, `and:ui/accounts/`, `and:ui/categories/`
- [ ] T09.1 [API] CRUD `/api/v1/accounts` (owner only; archive instead of delete if it has transactions)
- [ ] T09.2 [API] `/api/v1/categories`: list = defaults + own; CRUD on own only; defaults read-only
- [ ] T09.3 [API] Duplicate name per user → 409; delete category in use → 409 `IN_USE`
- [ ] T09.4 [AND·Sahan] Accounts list + add/edit screen
- [ ] T09.5 [AND·Sahan] Categories list grouped income/expense, add/edit own

**Done when**
- [ ] Tests: CRUD, duplicate → 409, in-use delete → 409, user B cannot see user A's (404), cannot edit a default category
- [ ] Screens create/edit/delete against local API

**Handoff notes:** —

---

### T10 · Income and expense records · `FR02`
**Lead:** Sevindu · **Support:** Sahan · **Depends on:** T09 · **Touches:** `api:transaction/`, `and:ui/transactions/`
- [ ] T10.1 [API] CRUD `/api/v1/transactions`; account and category must belong to user (or be default)
- [ ] T10.2 [API] List with filters `from`, `to`, `type`, `categoryId`, `accountId`, paging; sorted by `txn_date` desc
- [ ] T10.3 [API] `GET /api/v1/transactions/summary?from&to` → totals income/expense/net (BigDecimal)
- [ ] T10.4 [AND·Sahan] Transaction list with filters + edit/delete

**Done when**
- [ ] Tests: totals exact to the cent after create/edit/delete; filters; other user's account/category rejected; isolation 404

**Handoff notes:** —

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
