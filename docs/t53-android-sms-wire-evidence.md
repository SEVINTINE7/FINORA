# Execution 40 Android SMS wire evidence

Scope: D35 synthetic NSB fixtures on FINORA_T55_Evaluation, Android 17/API 37, emulator-5580,
private ADB port 5039 and Android user/serial 0. Base d94a79001de6fffdb3eee5b79068d1ce20d1c07d,
upstream main 5d99285077e192c3749a175e67e2b664b90e8e0b. The V1 acceptance app was unchanged
(lk.finora.app.acceptance, SHA-256 61566a20e6c82165d0dd4dfea85021f44d0dd48d39e0ca968736f51774eb62ed).
The rebuilt lk.finora.app.acceptance.test targeted it with AndroidJUnitRunner, SHA-256
a3f2b1fc4f0c673b09f676cc03a5a2fda19d2d46f624326a7a22ac0c55c6ddd2. Both used signer SHA-256
863fd92f2b8a4b4ff071062669525d5f866dbfa6752acfcef0f8eaac46092f6f.

One connected SmsPrivacyJourneyTest passed. Paste and inbox screens displayed POS debit LKR 1250.75
on 2026-11-21 with TEST SUPERMARKET, outward CEFT debit LKR 4500.25 on 2026-11-21 with no merchant,
and inward CEFT credit LKR 750.50 on 2026-11-22 with no merchant. The inbox held exactly three
synthetic test messages with sender NSB. Preview and confirmation ran through the app. Cancelling
the first preview and the first diagnostic prompt produced no extra requests.

The opt-in Spring test-classpath servlet capture observed only POSTs on the two listed routes.
It inspected the bodies transiently, excluded authorization headers and retained only sanitized
verdicts. No production code, security rule or HTTPS policy changed. The dedicated API had real
providers disabled and no provider credentials. Java HTTP(S) external requests were directed to
a closed loopback proxy. Process-wide network isolation was not independently verified.

| Sequence | POST endpoint | Fixture/source | API result | Delivered status |
|---|---|---|---|---|
| 1 | /api/v1/transactions/batch | POS / PASTE | 200, SAVED | 503 deliberately substituted after persistence |
| 2 | /api/v1/transactions/batch | POS / PASTE explicit retry | 200, DUPLICATE | 200 |
| 3 | /api/v1/transactions/batch | Outward CEFT / PASTE | 200, SAVED | 200 |
| 4 | /api/v1/transactions/batch | Inward CEFT / PASTE | 200, SAVED | 200 |
| 5 | /api/v1/support/sms-diagnostics | Synthetic unrecognized paste | 204 | 204 |
| 6 | /api/v1/transactions/batch | Inward CEFT, outward CEFT, POS / SMS | 200, three DUPLICATE | 200 |

All six passed the allowlist and raw-marker exclusion checks. Batch root was items. Each item
had dedupHash (64 lowercase hexadecimal characters) and transaction. Observed transaction
fields were accountId, amount, source, txnDate and type, plus merchant only for the POS item.
Optional categoryId was omitted. Diagnostic fields were bankCode, formatVersion and errorCode
with FORMAT_UNRECOGNIZED. The captured requests contained no unexpected field. Capture found no
raw message markers. The POS retry body matched the first body byte for byte and no automatic
replay was observed. The inbox batch verified the duplicate path against the earlier paste saves.
The fresh synthetic wallet went from 0 to exactly 3 rows, all attached to that wallet. There was
one new diagnostic row and no additional transaction from inbox duplicates or retry.

Two earlier screen harness attempts stopped before a write, at preview readiness and row opening.
The final app and test APK build passed. The captured six-request run preceded the scan-exclusion
annotation change to the capture configuration. That registration-only change leaves the capture
logic unchanged and was checked by starting the explicit test launcher again.

READ_SMS returned to ungranted. The temporary credential input and session reverse mapping were
removed. Only the dedicated emulator, private ADB server and API were stopped. The AVD and MySQL
instance were retained, and the loader was never invoked. Canonical profile read-back remained
826 transactions, 24 budgets, 2 bills, 18 paid markers, 2 goals, 18 contributions and 21 score
declarations, with original profile totals and transaction SHA-256
14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944.

This closes the tested screen-level synthetic SMS evidence gap locally. Dependency remediation,
OWASP Dependency-Check, authenticated deployed HTTPS and independent review remain open for T53.
