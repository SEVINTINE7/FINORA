# FINORA — Thilina's Tracker

**Role:** Analytics / AI / QA  
**Lead tasks:** 14 (42 subtasks) · **Supporting tasks:** 7  
**Master list:** [FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md](../FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md)

**Rules:** Tick a box only after the code is reviewed and real build/test output exists. Lead = you own delivery and tick the boxes here. Supporting = help the lead; boxes are ticked in the lead's tracker.

---

## Lead tasks

### 05 — Analytics and health score

- [ ] **T21 · Deterministic spending metrics** · `FR06`
  - [ ] Define and unit-test needs/wants, payday, weekend and fixed-bill metrics
  - [ ] Return every insight with exact source amounts and periods
  - [ ] Handle missing data, refunds and zero-income months

- [ ] **T22 · Spending-pattern detection** · `FR06`
  - [ ] Implement threshold rules approved by the team
  - [ ] Generate explainable insight records from calculated facts
  - [ ] Regression-test normal, boundary and extreme spending cases

- [ ] **T23 · Health-score model (0–100)** · `FR07`
  - [ ] Approve formula/weights for savings, spending control, debt, emergency savings and goals
  - [ ] Implement bounded deterministic calculation and part-by-part reasons
  - [ ] Test missing categories, zero income, boundary values and repeatability

### 06 — Rule-based and verified AI advice

- [ ] **T25 · Rule-based advice engine** · `FR08`
  - [ ] Generate allowed advice/actions from deterministic financial facts
  - [ ] Show calculation provenance for every numerical recommendation
  - [ ] Test logic across budget, savings and debt scenarios

- [ ] **T26 · AI-safe fact and response schema** · `FR09`
  - [ ] Whitelist aggregate numbers/category labels; prohibit names and identifiers
  - [ ] Define strict structured response containing only approved actions and claims
  - [ ] Create adversarial examples for hidden personal data and unsupported numbers

- [ ] **T27 · Server-only Groq integration** · `FR09` · with Sevindu
  - [ ] Call Groq from Spring Boot only; keep secret outside APK/Git
  - [ ] Handle provider failures, timeouts and rate limits
  - [ ] Capture minimal redacted diagnostics for QA

- [ ] **T28 · Gemini backup and rule fallback** · `FR09`
  - [ ] Implement configurable provider failure fallback
  - [ ] When provider or checker fails, show deterministic rule-based advice
  - [ ] Test rate-limit/timeout/invalid-response paths

- [ ] **T29 · Number and claim verification** · `FR09 / NFR-AIN`
  - [ ] Compare all generated numerical values to approved calculated facts
  - [ ] Reject unsupported numbers/claims and inconsistent units or periods
  - [ ] Test manipulated percentages, rounding and fabricated numbers

### 07 — Planning, history, alerts and data rights

- [ ] **T31 · Daily spending guide** · `FR14` · with Sahan
  - [ ] Calculate available daily spending until next income after bills/savings
  - [ ] Display assumptions and zero/negative availability safely
  - [ ] Test date and budget edge cases

- [ ] **T32 · Savings goal simulator** · `FR14` · with Sahan
  - [ ] Calculate scenario effects deterministically
  - [ ] Make simulated results clearly different from actual records
  - [ ] Test user inputs and rounding

### 11 — Tests, hardening and release

- [ ] **T52 · Three-role authorization tests** · `ROLE-CR` · with Sevindu
  - [ ] Test USER→USER, USER→STAFF, STAFF→ADMIN and revoked-role attempts
  - [ ] Verify Admin/Staff cannot read private income/expense data
  - [ ] Cover access by guessing IDs and endpoint calls

- [ ] **T53 · Privacy and security checks** · `NFR-SEC` · with Sevindu
  - [ ] Verify HTTPS, hashed passwords, token expiry and redacted logs
  - [ ] Inspect SMS upload and LLM requests for forbidden sensitive fields
  - [ ] Scan dependencies/secrets and fix high-severity findings

- [ ] **T54 · AI and financial correctness tests** · `FR06–FR09`
  - [ ] Test deterministic calculations with manually checked fixtures
  - [ ] Test number-check rejects hallucinated values and falls back safely
  - [ ] Measure number-check pass rate from actual trials/tests

- [ ] **T55 · SMS, usability and performance checks** · `NFR-SMS / NFR-USE / NFR-PERF` · with Sahan
  - [ ] Measure SMS field accuracy against the ≥90% proposal target
  - [ ] Benchmark main-screen load against 2-second target with one-year data
  - [ ] Run genuine usability tests and record SUS scores (target ≥70)

---

## Supporting tasks

- [ ] **T01 · Confirm implementation decisions** · `BASE` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T16 · SMS parser regression tests** · `FR03 / NFR-SMS` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T24 · Insights and score screens** · `FR06 / FR07` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T30 · Advice delivery and ratings** · `FR09 / FR10` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T50 · Backend API test suite** · `ALL` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T51 · Android functional tests** · `ALL` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T57 · End-to-end acceptance and handoff** · `ALL` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)

---

## Notes / blockers

- 
