# FINORA — T53 Security and Privacy Checklist

## 27 November 2026 — D59 staging

The reviewed repository revision is `f52f3163a59e05b47b28e46bbed5c0aa670c2851`. The T56 operator identifies the deployed API as `api-20261127T070942Z.jar`, built from `29e73a91b045c58f9a861d8a4a8d040636e17519`. The synchronized project checkout is older (`e53b8e5`).

| Requirement | Evidence and disposition |
|---|---|
| Trusted HTTPS | Execution 71 used the existing staging checker's default certificate trust and hostname verification for one anonymous GET to `https://finorafinance.duckdns.org/actuator/health`. HTTP 200, allowed JSON fields, status UP. Sevindu separately recorded a stock phone health check and a signed V1 Release invalid-login response. Successful signed-release login remains open. |
| Operations and recovery | Sevindu records loopback-only API/MySQL listeners, port 80 closed outside certificate renewal, restricted service files, schema-scoped application DML grants, and an encrypted backup restored into a separate schema with 34 equal table counts. These are operator observations. See the [T56 handoff](../trackers/sevindu/7-testing-release.md). |
| Authenticated staging checks | No run. The scoped operator approval, effective disabled-provider/Firebase attestation, two consented synthetic users and their owned account IDs, and a genuine expired token for user A were unavailable. No staging login, financial request or port probe was made by this execution. See the [harness procedure](t53-staging-verification.md). |
| T53.1 | Trusted public HTTPS, BCrypt and the retained expiry/application-log regressions have evidence. Exact lifetime boundaries and deployed access-log redaction/retention still require verification. |
| T53.2 | The [emulator screen capture](t53-android-sms-wire-evidence.md) covered six paste/inbox/diagnostic/retry requests. Both provider request builders have [loopback serialization and logging evidence](t53-local-security-evidence.md). The November 27 delta since `bf2af3d` did not change the SMS import or provider request builders. Coverage is synthetic and local. |
| T53.3 | Gitleaks 8.30.0, verified against the retained archive and checksum, scanned 44 commits in `b48be9b..f52f316` with full redaction. One generic-api-key finding at `StaffProvisioningControllerTest.java:80` in `fdab512` is the literal synthetic password supplied by the mocked activation test. No suppression. The existing eleven Android build-classpath advisory matches remain the retained assessment. The November 27 delta did not change the dependency declarations. OWASP still requires authorized fresh feed access or a trusted populated database. |

Execution 73 locally checked normal login issuance at exactly 30 minutes for the access JWT and 14 days for the stored refresh credential. The atomic refresh comparison rejected database-clock equality without revocation and accepted one microsecond of remaining life. The two new AuthControllerTest cases passed on disposable MySQL 8. These results supplement the earlier dated entries. Deployed log redaction and retention remain operator checks.

Independent security acceptance remains pending. The dated entries below retain their original verification scope.

## Current merged local verification

[PR #36](https://github.com/SEVINTINE7/FINORA/pull/36) supplies explicit JWT and refresh boundary regressions. Deployed access-log redaction and retention remain operator checks. [PR #37](https://github.com/SEVINTINE7/FINORA/pull/37) pins backend Guava to 33.7.2-jre and the OpenTelemetry BOM to 1.67.0. The retained Android build-classpath observations have a separate scope. The isolated AGP candidate left one critical finding and the Kotlin experiment failed compatibility, so neither experiment supplies an adopted correction. A current trusted OWASP data source remains outstanding.

PR #39 adds one ADMIN-only protected route and exercises response-field allowlisting, unchanged second-owner financial counts and old credential rejection. No new stage traffic, provider calls or scanner pass is claimed.

## Historical baseline

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

## Execution 33 evidence status

- Local provider runtime and application log privacy: verified for both adapters on loopback.
- Android structured SMS batch: verified at the repository wire boundary. End-to-end synthetic
  paste and inbox capture remains open.
- Differential secret scan: verified for the recorded commits and source paths.
- Dependencies: complementary OSV matches recorded. The OWASP report remains blocked.
- Deployed HTTPS, external provider runtime evidence and independent acceptance remain open.

See [Execution 33 security evidence](t53-local-security-evidence.md#execution-33-security-and-advice-verification).
