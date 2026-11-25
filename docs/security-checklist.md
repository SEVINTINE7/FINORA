# FINORA — T53 Security and Privacy Checklist

Audit date: 2026-11-22
Status: PARTIAL — NOT FINAL SECURITY ACCEPTANCE
Baseline: `2c788c7`, `codex/t53-security-baseline`; existing uncommitted expiry test preserved.

## Static inspection results

| Check | Finding | Status |
|---|---|---|
| Password hashing | BCrypt strength 12 in SecurityConfig.java | OBSERVED |
| Registration | PasswordEncoder.encode in AuthService.java | OBSERVED |
| Login | PasswordEncoder.matches in AuthService.java | OBSERVED |
| JWT expiry | Expiration set in JwtService.java | OBSERVED |
| JWT signature | verifyWith(key) in JwtService.java | OBSERVED |
| HTTPS | No enforcement found in searched application sources | UNVERIFIED |
| Error logging | Handler now logs exception type only; regression excludes raw URI/query and exception text | VERIFIED LOCALLY |

OBSERVED means the code pattern was found, not that complete security acceptance passed.

## Pending verification

- [ ] Exact JWT/refresh lifetime boundary tests (expired-token rejection is verified)
- [ ] Deployment HTTPS/TLS verification
- [ ] Sensitive-data logging review
- [ ] SMS import network privacy (T14)
- [ ] AI request privacy (T27)
- [ ] Comprehensive secret scan
- [ ] Dependency vulnerability scan and triage
- [ ] Final reviewer acceptance

No live SMS or LLM testing, comprehensive secret scan, or dependency vulnerability scan was performed.

## Local authentication and logging checks

- Focused Java 17/offline Maven run: 17 tests, zero failures/errors/skips, exit 0 (`api/target/t53-focused.log`): 16 authentication tests and one logging regression. Final full verify result is recorded in the T53 handoff.
- Final backend `verify`: 151/151 tests, zero failures/errors/skips, exit 0; JaCoCo service lines 602/610 (98.69%), gate passed (`api/target/t53-verify.log`, `api/target/site/jacoco/index.html`). Only isolated finora_test used; no dependency downloads.
- Refresh expiry fixture: clearing JPA managed state alone still returned 200. Direct JDBC Timestamp binding used local time for a UTC DATETIME column. Database `UTC_TIMESTAMP(6) - INTERVAL 5 MINUTE`, an expired-row assertion and EntityManager.clear correctly preserve HTTP 401 / INVALID_REFRESH_TOKEN. Production AuthService unchanged.
- Tests cover BCrypt cost 12, password validation, invalid credentials, access-token expiry/malformed/wrong signature, refresh rotation/replay, hashed storage, revocation, expiry and disabled-account rejection. The 14-day refresh lifetime is statically observed, not tested at its exact clock boundary.
- MockMvc assertion-failure dumps caused the synthetic password/JWT/refresh-token output. Authentication tests now set MockMvcPrint.NONE and check application logs exclude issued tokens. CI uploads Surefire artifacts on failure, so this suppression also protects those diagnostics; CI configuration unchanged.
- GlobalExceptionHandler previously logged raw request URI and full exception details. It now logs exception type only; ErrorLoggingPrivacyTest proves sensitive exception/path/query markers are absent. Error responses retain path per the existing contract; never put secrets in URLs. Deployment access logging remains unverified.
- Stateless security configuration limits finance routes to USER and checks disabled accounts. Existing authorization tests run in backend verify; future ADMIN/STAFF functionality still blocks full T52 acceptance.
- gitleaks and dependency-check were not found on PATH. Limited tracked-file git-grep checks found zero recognizable private-key blocks or Google/GitHub/Groq token patterns (exit 1 = no matches). This is not a comprehensive/history scan; no installation or vulnerability database request occurred.
- HTTPS is deployment-dependent: verify certificate, redirects, trusted forwarded headers and direct backend restrictions with Sevindu/T56. Local tests do not establish deployed TLS.

## Recovered work and integration

- Read-only inspection retained pipeline commits `72ec247` and `ed33c41`; fork head matches `ed33c41`. Clients have HTTPS, 15-second timeouts, one retry and metadata-only logs. Pipeline tries backup for transport failure, rules immediately for rejected claims; verification events exclude rejected text. Configuration defaults disabled.
- Exact eligible-claim matching remains provisional. Historical 253 passing tests belong to that pipeline branch, not this T53 run; no live provider privacy or pass-rate claim is made.
- GitHub confirmed PRs #5/#6/#7 merged. Only the two pipeline commits are unique relative to fetched origin/main. An approved pipeline PR may target main without unmerged parent work; no new PR or push performed.
- Outstanding: deployed TLS/proxy/log retention review, real-device SMS capture (T14), controlled provider privacy and final policy acceptance (T27/T29), comprehensive secret/history and OWASP dependency scans, human acceptance. T53 remains in-progress.

## Final PR compatibility verification

Preserved 8d38725 and merged origin/main a0b5dfb without rewriting history. Final offline Java 17
Maven verify: 206 tests, zero failures/errors/skips, exit 0; service lines 668/677 (98.67%),
JaCoCo gate passed (`api/target/final-t53-verify.log`). Increase from 151 reflects merged upstream
analytics/score/advice tests. Added-line checks found no recognizable provider tokens, private-key blocks
or serialized JWTs; synthetic test passwords/signing keys are deliberately test-only. This limited check
does not replace comprehensive scanning. git diff --check passed. No live calls or Android modifications.

## MD05 artifact checks

The unsigned V1 release APK was inspected for backup and transfer rules, cleartext blocking, absence of READ_SMS, common credential signatures, and app-owned calls to Android logging or HTTP body logging. The Android 17 emulator checked FLAG_SECURE on the login activity and its language dialog. The remaining device lifecycle, screenshot, Recents and sanitized runtime-log checks are tracked under T40.

## Execution 40 Android SMS wire evidence

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
