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
