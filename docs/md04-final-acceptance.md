# MD04 final acceptance follow-up

Baseline: `93b5eb5`. D41 authorizes the implementation and verification scope. Ownership, formal dependencies and independent review still apply.

## T33 scheduler

The five-minute retry and monthly trigger both compute the immediately previous Asia/Colombo month from the current instant. A missed first-of-month run can recover while that target remains current. If the service is down throughout a Colombo calendar month, the target advances on restart and the skipped month's missing rows are never reconsidered. Existing dirty rows still have their independent post-commit refresh path. D27 directs the previous-month job and recalc of existing snapshots. D41 preserves those semantics.

**Decision required from Sevindu:** approve a first eligible month, a maximum automatic lookback, whether users must already have existed in the target month, and whether figures recovered after downtime may be recomputed from current records. Proposed implementation: durable completed-month checkpoint starting at the approved cutoff, chronological bounded batches, per-user/month completion via existing uniqueness and D39 owner locking, no overwrite of clean snapshots, and explicit reporting for gaps outside the approved window. Preserve dirty-row refresh and user deletion cascade. Leave historical behavior unchanged until those boundaries are agreed.

## T30 concurrent delivery

Before V12, two first-delivery requests could call the provider chain independently. The V11 constraint kept the delivered ID stable but could not prevent duplicate upstream calls. V12 uses a short, owner-scoped MySQL transaction to claim a two-minute generation lease. The provider runs outside the database transaction. Another service instance returns the already stored rating and advice if available, or `503 ADVICE_PENDING` for explicit retry. A replaced or expired claim cannot publish. A processing failure releases its claim and a process crash leaves it reclaimable at expiry. The owner/month unique advice key, rating constraint and verified canonical fallback remain in place.

The expiry covers the configured provider attempt budget under normal execution. A process stalled past expiry can still have an upstream request in flight when another instance claims the work. Fencing prevents that stalled execution from publishing stale advice.

Provider regression tests exercise timeout, rejected output and fallback with mocks. Local configuration has providers disabled and no Groq/Gemini credentials and model configuration for authorized live acceptance. Live verification still requires the project-approved isolated configuration and a bounded synthetic run. This does not change T30's original verified fallback and on-device rating checkboxes.

Full Maven verification passed 317 tests with zero failures, errors or skipped tests and the coverage gate. This includes 27 D39 MySQL concurrency tests, the generation race, claim expiry, processing-failure recovery, and the history owner-deletion check. V11 upgraded to V12 in the MySQL test and isolated device schemas.

## Form-driven refresh matrix

See the [targeted closure checklist](md04-targeted-closure.md) for subsequent test evidence and the remaining rows.

PASS uses Android forms and a real isolated MySQL-backed API. BLOCKED means the full requested form-to-dependent-screen chain was not demonstrated. Existing API and prior screen checks are retained separately.

| Operation | Result | Evidence and remaining check |
|---|---|---|
| Account create, edit, delete | PASS | Account form and immediate list refresh on isolated emulator. |
| Category create, edit, delete | PASS | Category form and immediate list refresh on isolated emulator. |
| Manual transaction create, edit, delete | PASS | Exact LKR 731.20 and 731.21 and immediate list removal on emulator. Prior physical evidence covers dependent Home refresh. |
| Budget and bill create, edit, delete | PASS | Forms and immediate list refresh on emulator. |
| Savings goal create, edit, delete | PASS | Forms and immediate list refresh on emulator. |
| Goal contribution add and remove | PASS | Exact LKR 27.61 appeared after add and removal returned the detail screen to its empty state. |
| CSV import | PASS | System picker, selected-row preview, explicit confirmation and LKR 31.27 transaction in Records. |
| Paste SMS import | PASS | Approved synthetic NSB POS message extracted LKR 31.41. The Android preview required account selection and confirmation. Records showed the exact saved amount. |
| Inbox SMS import | PASS | The isolated emulator received a synthetic NSB credit. Local scan extracted LKR 33.47, the preview deselected unrelated synthetic rows, and explicit confirmation saved one row. Records showed the exact credit. |
| Score declarations | PASS | Saved LKR 77.23 dated November 20, 2026 through the form and reopened the editor to verify both values. |
| Monthly History read and Back | PASS | HMD exact October net and nullable-score behavior from the existing emulator evidence. |
| Historical transaction refresh | PASS | On the emulator, an Android transaction form create, edit and confirmed delete changed the existing October snapshot net from LKR 0.00 to −12.13, then −13.17, then back to 0.00. October's score stayed unavailable with no recorded income. |
| Advice rating | PASS | Prior HMD and emulator form writes with dropped-201 reconciliation. HMD also read the existing rating after upgrading the backend schema to V12. |
| Import refresh across Records, Home and Insights | PASS | Records showed all three imported rows. Home showed LKR 33.47 income, 62.68 expenses and −29.21 net. Insights showed the same exact income and consumption. |
| Full cross-destination refresh and physical recreation | BLOCKED | Full operation-by-screen refresh coverage and physical rotation remain open. Previously verified emulator configuration changes do not substitute for HMD rotation evidence. |

## HMD Android 14

The Sinhala History screen displayed the trend heading, exact net LKR 5,000.00 and score 92/100. Back returned to More and History reopened correctly. Sahan confirmed audible Sinhala TalkBack labels for the trend heading, October total, score and saved Advice rating. Complete TalkBack reading order was not independently established. The test profile would not let Sahan enable Auto-rotate. ADB still showed test-user Auto-rotate off and the display at orientation 0. No physical rotation pass was recorded. Test-profile timeout, locale and accessibility settings were restored, the temporary API mapping was removed, and Android returned to user 0.

## Retained localization and Android verification

All 354 English/Sinhala resource pairs match by key, XML type, format arguments and plural forms. The previous 96-pair and 64-pair approvals remain pinned to their reviewed values. The 22-pair History and Advice inventory still awaits native-speaker approval.

The V1 debug and unsigned release builds passed. Android reports 140 JVM tests, zero failed or skipped, and lint with no issues. The packaged release manifest excludes READ_SMS.

## Formal closure

MD04 remains 19/20: T20 5/5, T38 3/4, T24 4/4 and T30 7/7. T33 is tracked separately at 5/5. MD01–MD03 remain 48/48. No task was moved to review or done.

| Task | Formal prerequisites still open |
|---|---|
| T20 | T10, T17 |
| T38 | T09 |
| T24 | T23, T38 |
| T30 | T25, T29, T38 |
| T33 | T23 |

Independent human review remains required. Technical verdict for MD05: NO-GO while the final MD04 acceptance item remains open.
