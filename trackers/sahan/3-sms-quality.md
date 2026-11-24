# Sahan 3 — SMS preview, duplicates, accuracy tests (T15, T16)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.

**MD03 — 9/9 verified.** T15 and T16 remain in progress pending formal T10/T14 prerequisites as applicable and independent review. [Development handoff](../../docs/md05-delivery-handoff.md).
**Detail: outline** — expand before coding.

---

### T15 · Transaction preview and deduplication · `FR03`
**Lead:** Sahan · **Support:** Sevindu · **Depends on:** T14, T10 · **Touches:** `and:ui/sms/`, `api:transaction/`
- [x] T15.1 [AND] Preview list of parsed SMS: edit amount/date/merchant/category before saving; select/deselect
  - D37 scope: an in-memory, USER-session-bound preview for typed inbox/paste results. Edit an exact LKR amount, calendar date, merchant, account and type-compatible optional category; toggle inclusion. D38 adds explicit confirmation before a batch write.
- [x] T15.2 [AND] Compute the D38 reference-less SHA-256 fingerprint before preview edits; report potential duplicates for review
- [x] T15.3 [API·Sevindu] `POST /api/v1/transactions/batch` (source=SMS/PASTE); duplicate hash → reported, not saved
- [x] T15.4 [AND] Keep a frozen structured batch in screen/session memory on uncertain outcomes; retry only on explicit confirmation

**Done when**
- [x] Tests: same SMS imported twice → saved once; manual entry matching an SMS flagged; retry after network loss

**Handoff notes:**
- D39 fix `5e3abc1` serializes account removal and transaction writes. MySQL reproduced the original loss before the fix. All 27 regression cases passed across both deletion orderings, archival, ownership, atomicity, deduplication and intentional user deletion. D26/D37/D38 and formal review still apply.
- D38 fingerprints original local date, direction, exact cent amount and normalized merchant for either source. An unresolved equal fingerprint is reported for review; confirmed distinct records can be entered manually.
- Android 17 isolated emulator: confirmed Paste saved one and reported a manual match. The equivalent inbox row was flagged, two distinct CEFT rows saved, and a deliberately lost response led to no auto-replay. The explicit identical retry saved zero and the original manual row remained.
- D39 verification passed 304 backend tests and the coverage gate. Android V1 debug built and passed 136 JVM tests, with zero lint errors and one existing budget-format warning. Release manifest omits READ_SMS.
- Three inspected batch requests contained only permitted fields. No private fixture markers appeared in 327 FINORA app log lines. Pending writes remain in memory for the active screen and login. T10/T14 gates and independent review remain.
- Audit of cd3ab57: fixed retry loss on rotation or a new scan/paste action, and added indexed 400 responses for mistyped rows. Android 17 verified rotation retention and process-death clearing, both without automatic replay. Six focused backend and 25 focused Android tests passed, with debug build and lint successful.

---

### T16 · SMS parser regression tests · `FR03 / NFR-SMS`
**Lead:** Sahan · **Support:** Thilina · **Depends on:** T14 · **Touches:** `android/app/src/test/resources/sms/`, `and:sms/`
- [x] T16.1 Use the D38-approved synthetic-only D35 fixture set with documented expected fields
- [x] T16.2 Cover every supported bank: debit, credit, reversal, ATM, malformed
- [x] T16.3 Test computes field-level accuracy and prints it; target ≥ 90%

**Done when**
- [x] Accuracy report from a real test run saved in Handoff notes

**Handoff notes:**
- D37 permits isolated Android verification while T14 waits for formal closure. Sevindu approved synthetic-only T16.1 under D38, using the three D35 NSB fixtures. No original customer SMS was collected.
- SmsAccuracyTest scored 12/12 expected amount, date, direction and merchant fields (100.0%) across three positives. Missing CEFT merchants counted as expected absent values.
- Six negative inputs went to manual review: unknown, malformed, ambiguous amount, reversal, ATM cash withdrawal and invalid date. Only NSB POS debit and the two approved CEFT formats are supported.
- V1 debug built, 132 JVM tests passed, and lint reported zero errors and one existing budget-format warning. Formal dependency closure and independent review remain open.
