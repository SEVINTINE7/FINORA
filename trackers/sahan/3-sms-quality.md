# Sahan 3 — SMS preview, duplicates, accuracy tests (T15, T16)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T15 · Transaction preview and deduplication · `FR03`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T14, T10 · **Touches:** `and:ui/sms/`, `api:transaction/`
- [ ] T15.1 [AND] Preview list of parsed SMS: edit amount/date/merchant/category before saving; select/deselect
- [ ] T15.2 [AND] `dedup_hash` = SHA-256(sender + date + amount + last-4-digit reference) computed on phone; skip already-imported
- [ ] T15.3 [API·Sevindu] `POST /api/v1/transactions/batch` (source=SMS/PASTE); duplicate hash → reported, not saved
- [ ] T15.4 [AND] Failed sync kept locally and retried; no duplicates after retry

**Done when**
- [ ] Tests: same SMS imported twice → saved once; manual entry matching an SMS flagged; retry after network loss

**Handoff notes:** —

---

### T16 · SMS parser regression tests · `FR03 / NFR-SMS`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T14 · **Touches:** `android/app/src/test/resources/sms/`, `and:sms/`
- [ ] T16.1 Collect consented SMS, redact names/account numbers, store as test fixtures with expected fields
- [ ] T16.2 Cover every supported bank: debit, credit, reversal, ATM, malformed
- [ ] T16.3 Test computes field-level accuracy and prints it; target ≥ 90%

**Done when**
- [ ] Accuracy report from a real test run saved in Handoff notes

**Handoff notes:** —
