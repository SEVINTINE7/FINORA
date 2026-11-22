# Thilina 5 — QA: roles, security, AI correctness, SMS/usability/performance (T52–T55)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting.
T52 backend extension (authorized 2026-11-22): discover existing financial mappings; seed synthetic owner data;
for STAFF and ADMIN assert exact standard 403 fields and unchanged financial rows after every denied read/write.
No new endpoints, financial policy, production changes or repeated foreign-owner checks.

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
- 2026-11-22: FinancialResponsePrivacyTest focused run 4/4 passed, zero failures/errors/skips, exit 0: each of STAFF/ADMIN denied across 19 real financial GET mappings and 23 write mappings. Exact error fields/messages and synthetic-data exclusions checked; ten financial tables unchanged after every request, including GET insights side effects. Owner positive controls verify fixture accessibility.
- Final offline Java 17 Maven verify: 206/206 passed, zero failures/errors/skips, exit 0; service line coverage 668/677 (98.67%), gate passed. Evidence api/target/final-t52-focused.log and final-t52-verify.log; git diff --check passed. Existing foreign-resource checks untouched; no production/Android/config changes. T41/T46 and CI acceptance remain blocked; T52 stays in-progress.

---

### T53 · Privacy and security checks · `NFR-SEC`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T14, T27 · **Touches:** `docs/security-checklist.md`
- [ ] T53.1 Verify HTTPS, BCrypt hashes, token expiry, redacted logs
- [ ] T53.2 Capture app traffic (proxy) during SMS import: no SMS body; capture LLM request: no forbidden fields
- [ ] T53.3 Run OWASP dependency-check + secret scan (e.g. gitleaks); fix high findings

**Done when**
- [ ] `docs/security-checklist.md` filled with real results

**Handoff notes:**
- 2026-11-22 final handoff: open PR #9 preserves 8d38725 and verified local authentication/log protection. Upstream-compatible verify 206/206 passed, zero failures/errors/skips, exit 0; coverage gate passed. Deployed HTTPS/proxy, device/provider traffic, comprehensive scans and human acceptance remain pending. T53 stays in-progress; full evidence/checklist lives in PR #9.

---

### T54 · AI and financial correctness tests · `FR06–FR09`
**Lead:** Thilina · **Depends on:** T29 · **Touches:** `api-test:advice/`
- [ ] T54.1 Fixture set of user-months with hand-checked metrics and scores
- [ ] T54.2 Run real LLM calls on fixtures; record pass rate of number check
- [ ] T54.3 Confirm every failure fell back to rule advice

**Done when**
- [ ] Pass-rate report from actual runs saved

**Handoff notes:**
- 2026-11-22 final handoff: mocked pipeline tests are in PR #8 (51 focused / 253 full passing). Actual LLM pass-rate evaluation remains blocked by T29 acceptance and explicit live-call/credential/budget authorization; no real pass rate measured. T54 stays todo; see docs/thilina-team-handoff.md.

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
- 2026-11-22 final handoff: T16/T38 and Sahan's real-device/user measurements block completion. No SMS accuracy, latency or SUS results fabricated. Beta comparison must be reconciled with D03 V1-only scope before evaluation; T55 stays todo.
