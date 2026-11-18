# FINORA — LEAN IMPLEMENTATION TRACKER

**Purpose:** Tasks and subtasks needed to build FINORA. No assignment/report/viva content. Use this file as the only implementation checklist in ChatGPT and Claude sessions.

**Stack:** Android Java (Android 8.0+) · Spring Boot/Java 17 REST API · MySQL 8 · server-side Groq (Gemini backup) · Firebase Cloud Messaging · Sinhala/English · LKR.

**Team:** Sevindu = API/database/admin · Sahan = Android/UI/SMS · Thilina = analytics/AI/QA. **ChatGPT and Claude are coding/review tools, not the app’s AI provider.**

**Scope:** Original proposal FR01–FR15 (13 Must, 2 Should). Added roles: User 10 functions, Admin 5, Staff 4. **Admin/Staff expansion needs group/lecturer approval before release.**

**Non-negotiables:** SMS content stays on device; only user-confirmed fields reach server. Never send names/bank details to LLM. Check every AI number; fallback to rules. No bank login. User data isolated by account and roles. All sensitive operations need real tests.

**Workflow:** Pick ONE task ID → assign ChatGPT or Claude to build → other model reviews → human runs tests → tick only verified items. Do not paste the whole tracker into every prompt; paste the task and short context above. `[ ]` = not yet verified, not proof nothing was done.

**Task prompt:** `FINORA: Implement Txx only. Follow stack and privacy rules above. Provide changed files, tests, exact run commands, risks and a brief handoff. Do not claim tests passed without output.`

---

## 00 — Foundation and architecture

- [ ] **T01 · Confirm implementation decisions** · Sevindu + all · `BASE`
  - [ ] Confirm Admin/Staff expansion with lecturer/group before enabling the new roles
  - [ ] Agree health-score formula, thresholds, API contracts and test strategy
  - [ ] Record only decisions that affect development

- [ ] **T02 · Repository and build setup** · Sevindu · `BASE`
  - [ ] Create API, Android and shared-docs folders/repos; add ignore rules
  - [ ] Set Java 17, Gradle/Maven, Android 8.0+ and MySQL 8 environments
  - [ ] Add clean-clone build/run instructions and CI build checks

- [ ] **T03 · Database design and migrations** · Sevindu · `FR01–FR15`
  - [ ] Define users/roles, records, categories, budgets, bills, goals, insights, advice and ratings
  - [ ] Create indexed MySQL schema with decimal money fields and foreign keys
  - [ ] Add versioned migrations and seed only non-sensitive reference data
  - [ ] Test migrations and ownership constraints

- [ ] **T04 · Backend API foundation** · Sevindu · `ALL`
  - [ ] Create Spring Boot project with DTOs, validation and standard errors
  - [ ] Configure dev/test/prod settings, secrets and CORS policy
  - [ ] Add API versioning, OpenAPI contract, health check and test profile

## 01 — Authentication, consent and permissions

- [ ] **T05 · Secure authentication** · Sevindu · `FR01`
  - [ ] Implement register/login/logout, password hashing and expiring tokens
  - [ ] Validate credentials and rate-limit authentication attempts
  - [ ] Test invalid login, expired tokens and account disablement

- [ ] **T06 · Role-based access control** · Sevindu · `ROLE-CR`
  - [ ] Define USER, ADMIN and STAFF permissions, default deny
  - [ ] Enforce permissions on every API endpoint and service
  - [ ] Test user-to-user isolation, horizontal and vertical privilege escalation

- [ ] **T07 · Consent and account settings** · Sevindu + Sahan · `FR01 / FR11`
  - [ ] Capture over-18 confirmation and informed consent with version/time
  - [ ] Implement profile language and account preferences
  - [ ] Provide consent withdrawal and data-deletion entry points

- [ ] **T08 · Three-role login routing** · Sahan + Sevindu · `ROLE-CR`
  - [ ] Route authenticated users to their permitted dashboard
  - [ ] Hide unauthorized screens and actions but enforce security on server
  - [ ] Test role switch, session expiry and disabled accounts

## 02 — User records and imports

- [ ] **T09 · Accounts and categories** · Sevindu + Sahan · `FR02`
  - [ ] Build CRUD API and Android screens for accounts/categories
  - [ ] Validate currency LKR, user ownership and safe deletion
  - [ ] Test duplicates and invalid input

- [ ] **T10 · Income and expense records** · Sevindu + Sahan · `FR02`
  - [ ] Create add/edit/delete/list endpoints and mobile forms
  - [ ] Store money as precise decimals with consistent dates/categories
  - [ ] Test totals, edits, filters and account isolation

- [ ] **T11 · Manual transaction entry** · Sahan · `FR02 / FR03`
  - [ ] Make offline-permission-independent manual entry available
  - [ ] Add required-field validation and user confirmation
  - [ ] Show clear import/entry errors without losing entered values

- [ ] **T12 · CSV import** · Sevindu + Sahan · `FR03`
  - [ ] Define CSV template, mapping, dates and amount validation
  - [ ] Preview/confirm imported records and flag duplicates
  - [ ] Reject malformed/oversized files and CSV formula-injection payloads

## 03 — Bank SMS import

- [ ] **T13 · SMS access and fallback** · Sahan · `FR03`
  - [ ] Check current Android permission/distribution restrictions on real devices
  - [ ] Request permission only with clear explanation and refusal path
  - [ ] Offer paste/CSV/manual options without SMS permission

- [ ] **T14 · Local bank SMS parsing** · Sahan · `FR03`
  - [ ] Create local SMS parser for supported bank formats
  - [ ] Extract amount/date/merchant where available; handle unknown formats
  - [ ] Never upload full SMS text; send only user-confirmed minimum fields

- [ ] **T15 · Transaction preview and deduplication** · Sahan + Sevindu · `FR03`
  - [ ] Display extracted details for user confirmation/editing
  - [ ] Detect duplicate SMS, repeated import and manual duplicates
  - [ ] Save only confirmed records; test failed sync and retry

- [ ] **T16 · SMS parser regression tests** · Sahan + Thilina · `FR03 / NFR-SMS`
  - [ ] Build consented/redacted SMS test dataset with bank-format coverage
  - [ ] Measure field-level accuracy against the ≥90% target
  - [ ] Test malformed, reversal, credit/debit and permission-denied cases

## 04 — Budgets, bills, goals and dashboard

- [ ] **T17 · Monthly budgets** · Sevindu + Sahan · `FR04`
  - [ ] Implement budget CRUD and month/category limits
  - [ ] Calculate used/remaining amounts against confirmed transactions
  - [ ] Show overspend states and tests for boundary dates

- [ ] **T18 · Monthly bills** · Sevindu + Sahan · `FR04`
  - [ ] Implement bill schedule, due dates and paid/unpaid state
  - [ ] Integrate bill totals into budget/planning calculations
  - [ ] Test month transitions and duplicate reminders

- [ ] **T19 · Savings goals** · Sevindu + Sahan · `FR04`
  - [ ] Create goal CRUD, targets, dates and contributions
  - [ ] Compute progress correctly without double-counting
  - [ ] Test edits, deletions and invalid targets

- [ ] **T20 · Dashboard and charts** · Sahan + Sevindu · `FR05`
  - [ ] Build totals and category trend APIs with clear date filters
  - [ ] Create Android dashboard/charts and empty states
  - [ ] Reconcile chart totals against raw records and manual calculations

## 05 — Analytics and health score

- [ ] **T21 · Deterministic spending metrics** · Thilina · `FR06`
  - [ ] Define and unit-test needs/wants, payday, weekend and fixed-bill metrics
  - [ ] Return every insight with exact source amounts and periods
  - [ ] Handle missing data, refunds and zero-income months

- [ ] **T22 · Spending-pattern detection** · Thilina · `FR06`
  - [ ] Implement threshold rules approved by the team
  - [ ] Generate explainable insight records from calculated facts
  - [ ] Regression-test normal, boundary and extreme spending cases

- [ ] **T23 · Health-score model (0–100)** · Thilina · `FR07`
  - [ ] Approve formula/weights for savings, spending control, debt, emergency savings and goals
  - [ ] Implement bounded deterministic calculation and part-by-part reasons
  - [ ] Test missing categories, zero income, boundary values and repeatability

- [ ] **T24 · Insights and score screens** · Sahan + Thilina · `FR06 / FR07`
  - [ ] Display evidence behind insights and every score component
  - [ ] Add clear explanations and not-financial-advice wording where appropriate
  - [ ] Verify mobile results match backend test fixtures

## 06 — Rule-based and verified AI advice

- [ ] **T25 · Rule-based advice engine** · Thilina · `FR08`
  - [ ] Generate allowed advice/actions from deterministic financial facts
  - [ ] Show calculation provenance for every numerical recommendation
  - [ ] Test logic across budget, savings and debt scenarios

- [ ] **T26 · AI-safe fact and response schema** · Thilina · `FR09`
  - [ ] Whitelist aggregate numbers/category labels; prohibit names and identifiers
  - [ ] Define strict structured response containing only approved actions and claims
  - [ ] Create adversarial examples for hidden personal data and unsupported numbers

- [ ] **T27 · Server-only Groq integration** · Thilina + Sevindu · `FR09`
  - [ ] Call Groq from Spring Boot only; keep secret outside APK/Git
  - [ ] Handle provider failures, timeouts and rate limits
  - [ ] Capture minimal redacted diagnostics for QA

- [ ] **T28 · Gemini backup and rule fallback** · Thilina · `FR09`
  - [ ] Implement configurable provider failure fallback
  - [ ] When provider or checker fails, show deterministic rule-based advice
  - [ ] Test rate-limit/timeout/invalid-response paths

- [ ] **T29 · Number and claim verification** · Thilina · `FR09 / NFR-AIN`
  - [ ] Compare all generated numerical values to approved calculated facts
  - [ ] Reject unsupported numbers/claims and inconsistent units or periods
  - [ ] Test manipulated percentages, rounding and fabricated numbers

- [ ] **T30 · Advice delivery and ratings** · Sahan + Thilina · `FR09 / FR10`
  - [ ] Display checked AI or fallback advice with readable source context
  - [ ] Add 1–5 advice rating UI and rating storage
  - [ ] Test privacy, duplicate ratings and honest AI/rules provenance

## 07 — Planning, history, alerts and data rights

- [ ] **T31 · Daily spending guide** · Thilina + Sahan · `FR14`
  - [ ] Calculate available daily spending until next income after bills/savings
  - [ ] Display assumptions and zero/negative availability safely
  - [ ] Test date and budget edge cases

- [ ] **T32 · Savings goal simulator** · Thilina + Sahan · `FR14`
  - [ ] Calculate scenario effects deterministically
  - [ ] Make simulated results clearly different from actual records
  - [ ] Test user inputs and rounding

- [ ] **T33 · Monthly insights and score history** · Sevindu + Sahan · `FR15`
  - [ ] Store monthly snapshots consistently and idempotently
  - [ ] Show prior scores and trends with clear dates
  - [ ] Test deletions and recalculation after corrections

- [ ] **T34 · Budget, bill and goal alerts** · Sevindu + Sahan · `FR13 (Should)`
  - [ ] Create server notification events and device token management
  - [ ] Use FCM with privacy-safe lock-screen content and opt-out
  - [ ] Test duplicate delivery, permission refusal and notification expiry

- [ ] **T35 · Data download** · Sevindu + Sahan · `FR11`
  - [ ] Export only authenticated user-owned data in a documented format
  - [ ] Use safe filenames/headers; test completeness and unauthorized requests
  - [ ] Remove temporary export files and never log sensitive content

- [ ] **T36 · Data deletion** · Sevindu + Sahan · `FR11`
  - [ ] Implement confirmation, deletion of dependent records and token invalidation
  - [ ] Define backup-retention behavior before launch
  - [ ] Test deletion/re-registration and privacy audit trail without sensitive payloads

## 08 — Android app experience

- [ ] **T37 · Mobile app skeleton** · Sahan · `ALL`
  - [ ] Set up Android Java app, navigation and dependency injection/repository boundary
  - [ ] Configure secure HTTPS API client and authentication expiry handling
  - [ ] Add loading/empty/error states and real-device smoke test

- [ ] **T38 · Financial screens and navigation** · Sahan · `FR02–FR15`
  - [ ] Connect records, import, budgets, dashboard and goal screens to API
  - [ ] Connect insights, health score, advice, history and settings screens
  - [ ] Test screen-state consistency after create/update/delete

- [ ] **T39 · Sinhala and English support** · Sahan · `FR01`
  - [ ] Move all user-visible strings to localized resources
  - [ ] Review Sinhala wording with a native speaker
  - [ ] Test long labels, accessibility and language switching

- [ ] **T40 · Android privacy and resilience** · Sahan · `NFR-SEC`
  - [ ] Keep tokens secure; no model/API keys or full SMS in logs
  - [ ] Minimize local storage and protect sensitive backups/screens
  - [ ] Test background, network loss, rotation and expired-session behaviour

## 09 — Admin login and five functions (gated change)

- [ ] **T41 · Admin login/dashboard** · Sevindu · `ADMIN-01`
  - [ ] Build secure ADMIN-only login route and compact status dashboard
  - [ ] Reject all non-admin access including forged tokens
  - [ ] Do not display user financial contents

- [ ] **T42 · Admin manage user accounts** · Sevindu · `ADMIN-02`
  - [ ] List safe account-status fields and search by permitted identifiers
  - [ ] Enable/disable account with reason and audit event
  - [ ] Test that Admin cannot read or edit user transactions

- [ ] **T43 · Admin manage staff accounts** · Sevindu · `ADMIN-03`
  - [ ] Create/disable STAFF accounts with least-privilege permissions
  - [ ] Prevent staff creation of admins or self-escalation
  - [ ] Test revocation and expired sessions

- [ ] **T44 · Admin default categories** · Sevindu · `ADMIN-04 / FR12`
  - [ ] CRUD default income/expense categories without altering user-owned records
  - [ ] Validate name uniqueness and safe deactivation
  - [ ] Test unauthorized edits

- [ ] **T45 · Admin bank SMS formats** · Sevindu · `ADMIN-05 / FR12`
  - [ ] Manage parser format templates with versions and validation
  - [ ] Publish safe updates for supported banks without exposing real SMS data
  - [ ] Test rollout/rollback and parser regressions

## 10 — Staff login and four functions (gated change)

- [ ] **T46 · Staff login/dashboard** · Sevindu + Sahan · `STAFF-01`
  - [ ] Build STAFF-only login and limited support dashboard
  - [ ] Ensure staff cannot access admin settings or private financial records
  - [ ] Test blocked endpoints and disabled accounts

- [ ] **T47 · Staff support requests** · Sevindu + Sahan · `STAFF-02`
  - [ ] Build support ticket workflow with user-submitted redacted details
  - [ ] Allow status and response updates only on assigned/permitted tickets
  - [ ] Test ticket isolation and attachment sanitization

- [ ] **T48 · Staff application assistance** · Sahan · `STAFF-03`
  - [ ] Provide non-sensitive help for login, navigation and common app issues
  - [ ] Create reusable help responses without requesting bank passwords or SMS text
  - [ ] Test visibility and permission limits

- [ ] **T49 · Staff SMS import troubleshooting** · Sahan + Sevindu · `STAFF-04`
  - [ ] Accept sanitized error codes and format mismatch reports only
  - [ ] Let staff flag issues for Admin without editing parser templates
  - [ ] Test that raw SMS, account identifiers and user finances are inaccessible

## 11 — Tests, hardening and release

- [ ] **T50 · Backend API test suite** · Sevindu + Thilina · `ALL`
  - [ ] Add JUnit/Mockito integration tests for endpoints, validation and ownership
  - [ ] Run real MySQL-backed migration/regression suite
  - [ ] Attach actual passing command output and fix failures

- [ ] **T51 · Android functional tests** · Sahan + Thilina · `ALL`
  - [ ] Add UI tests for registration, records, import and main features
  - [ ] Test Android 8.0+ and recent devices with permission denied
  - [ ] Capture actual screenshots/test logs, never fabricated evidence

- [ ] **T52 · Three-role authorization tests** · Thilina + Sevindu · `ROLE-CR`
  - [ ] Test USER→USER, USER→STAFF, STAFF→ADMIN and revoked-role attempts
  - [ ] Verify Admin/Staff cannot read private income/expense data
  - [ ] Cover access by guessing IDs and endpoint calls

- [ ] **T53 · Privacy and security checks** · Thilina + Sevindu · `NFR-SEC`
  - [ ] Verify HTTPS, hashed passwords, token expiry and redacted logs
  - [ ] Inspect SMS upload and LLM requests for forbidden sensitive fields
  - [ ] Scan dependencies/secrets and fix high-severity findings

- [ ] **T54 · AI and financial correctness tests** · Thilina · `FR06–FR09`
  - [ ] Test deterministic calculations with manually checked fixtures
  - [ ] Test number-check rejects hallucinated values and falls back safely
  - [ ] Measure number-check pass rate from actual trials/tests

- [ ] **T55 · SMS, usability and performance checks** · Thilina + Sahan · `NFR-SMS / NFR-USE / NFR-PERF`
  - [ ] Measure SMS field accuracy against the ≥90% proposal target
  - [ ] Benchmark main-screen load against 2-second target with one-year data
  - [ ] Run genuine usability tests and record SUS scores (target ≥70)

- [ ] **T56 · Deployment and backups** · Sevindu · `RELEASE`
  - [ ] Harden Ubuntu server, MySQL access, HTTPS and environment secrets
  - [ ] Set scheduled encrypted backups and perform a restore drill
  - [ ] Deploy API, migrations and signed Android APK with rollback plan

- [ ] **T57 · End-to-end acceptance and handoff** · All · `ALL`
  - [ ] Demonstrate user signup→record→analysis→advice→planning on a real device
  - [ ] Demonstrate Admin and Staff only if role change is approved
  - [ ] Review open defects and document only verified working features

---

**Completion rule:** A task is complete only after its checked subtasks, code review and actual passing build/test evidence. If a requirement changes, update its tasks before coding. No task is claimed complete in this initial tracker.
