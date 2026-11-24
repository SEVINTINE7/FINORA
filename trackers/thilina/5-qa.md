# Thilina 5 — QA: roles, security, AI correctness, SMS/usability/performance (T52–T55)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting.

---

### T52 · Three-role authorization tests · `ROLE-CR`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T06, T41, T46 · **Touches:** `api-test:security/`
- [ ] T52.1 Matrix test: every endpoint × {none, USER, other USER, STAFF, ADMIN, disabled} → expected status
- [ ] T52.2 ID-guessing tests on every `{id}` endpoint
- [ ] T52.3 Confirm Admin/Staff responses never include amounts or transactions

**Done when**
- [ ] Matrix test green in CI

**Handoff notes:**
- 2026-11-21 Thilina: Added disabled-account JWT checks for USER, STAFF and ADMIN across discovered protected endpoints. EndpointAuthTest: 6/6 PASS, 0 failures/errors/skips, Java 17 and MySQL 8. Full role matrix, ownership tests, privacy checks and CI confirmation remain pending.
- 2026-11-22 audit: current T53 verify includes EndpointAuthTest and RoleAccessTest; existing discovery covers implemented endpoints, but does not assert exact error-field privacy. Highest-priority independent follow-up: T52.3 response-field privacy assertions on existing finance endpoints, on a separate T52 branch. T41/T46 and CI acceptance remain blocked; no new T52 tests or completion claimed here.

---

### T53 · Privacy and security checks · `NFR-SEC`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T14, T27 · **Touches:** `docs/security-checklist.md`
- [ ] T53.1 Verify HTTPS, BCrypt hashes, token expiry, redacted logs
- [ ] T53.2 Capture app traffic (proxy) during SMS import: no SMS body; capture LLM request: no forbidden fields
- [ ] T53.3 Run OWASP dependency-check + secret scan (e.g. gitleaks); fix high findings

**Done when**
- [ ] `docs/security-checklist.md` filled with real results

**Handoff notes:**
- 2026-11-22: Initial static security audit recorded in docs/security-checklist.md. BCrypt strength 12, password encoding/matching and JWT expiry/signature verification located. HTTPS deployment, log redaction, live SMS/AI privacy, comprehensive secret scanning and dependency scanning remain unverified. T53 in-progress.
- 2026-11-22 recovery: fixed pre-existing expiry fixture using database UTC and EntityManager.clear; clearing managed state alone still failed. Preserved 401 INVALID_REFRESH_TOKEN; AuthService unchanged. Added disabled-refresh/wrong-signature regressions and disabled auth MockMvc payload dumps.
- Unexpected-error logger now omits raw URLs and exception details; dedicated privacy regression added. Focused 17/17 passed; final offline Java 17 Maven verify: 151/151, zero failures/errors/skips, exit 0, isolated finora_test only (`api/target/t53-verify.log`).
- JaCoCo service gate PASS: 602/610 lines (98.69%); report api/target/site/jacoco/index.html. Limited tracked-content secret patterns found no matches; gitleaks/dependency-check not on PATH, so comprehensive scans remain unchecked.
- Recovered pipeline commits 72ec247/ed33c41 match fork branch; static inspection confirms mock-only tests, strict provisional verifier, bounded audit metadata and rule fallback. PRs #5/#6/#7 confirmed merged; no new PR/push, no live calls or policy changes.
- Final PR compatibility: preserved 8d38725, merged origin/main a0b5dfb; offline verify 206/206 passed, zero failures/errors/skips, exit 0; service lines 668/677 (98.67%), gate passed (api/target/final-t53-verify.log). Pipeline PR #8 open; next: reviewer acceptance, T14 capture, deployed TLS/proxy/log review and comprehensive scans. T53 remains in-progress.

---

### T54 · AI and financial correctness tests · `FR06–FR09`
**Lead:** Thilina · **Depends on:** T29 · **Touches:** `api-test:advice/`
- [ ] T54.1 Fixture set of user-months with hand-checked metrics and scores
- [ ] T54.2 Run real LLM calls on fixtures; record pass rate of number check
- [ ] T54.3 Confirm every failure fell back to rule advice

**Done when**
- [ ] Pass-rate report from actual runs saved

**Handoff notes:**
- 2026-11-22 audit: recovered T27-T29 branch contains mocked client/verifier/fallback tests; historical 253-test result is branch-specific and was not rerun here. Real LLM pass-rate testing remains unauthorized; T29 acceptance blocks T54 completion. Do not report mocked results as real pass rates.
- D44 now authorizes a bounded synthetic live check for T30 once approved models, isolated credentials and a numeric call budget are available. The T54 pass-rate evaluation and its T29 prerequisite remain open.

---

### T55 · SMS, usability and performance checks · `NFR-SMS / NFR-USE / NFR-PERF`
**Lead:** Thilina · **Support:** Sahan · **Depends on:** T16, T38 · **Touches:** `docs/evaluation.md`
- [ ] T55.1 SMS field accuracy from T16 ≥ 90%
- [ ] T55.2 Seed one year of data; measure main screens < 2 s on a mid-range phone
- [ ] T55.3 SUS questionnaire with trial users; target ≥ 70
- [ ] T55.4 Beta advice ratings: compare RULE vs AI with a statistical test

**Done when**
- [ ] `docs/evaluation.md` contains real measured numbers

**Handoff notes:**
- 2026-11-22 audit: T16/T38 remain todo; no device timing, SMS accuracy, SUS participants or ratings measurements available. T55 remains todo. Beta comparison wording also needs reconciliation with D03 V1-only scope before evaluation; no measurements invented.
