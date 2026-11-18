# FINORA — Sevindu's Tracker

**Role:** API / database / admin  
**Lead tasks:** 27 (82 subtasks) · **Supporting tasks:** 7  
**Master list:** [FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md](../FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md)

**Rules:** Tick a box only after the code is reviewed and real build/test output exists. Lead = you own delivery and tick the boxes here. Supporting = help the lead; boxes are ticked in the lead's tracker.

---

## Lead tasks

### 00 — Foundation and architecture

- [ ] **T01 · Confirm implementation decisions** · `BASE` · with all
  - [ ] Confirm Admin/Staff expansion with lecturer/group before enabling the new roles
  - [ ] Agree health-score formula, thresholds, API contracts and test strategy
  - [ ] Record only decisions that affect development

- [ ] **T02 · Repository and build setup** · `BASE`
  - [ ] Create API, Android and shared-docs folders/repos; add ignore rules
  - [ ] Set Java 17, Gradle/Maven, Android 8.0+ and MySQL 8 environments
  - [ ] Add clean-clone build/run instructions and CI build checks

- [ ] **T03 · Database design and migrations** · `FR01–FR15`
  - [ ] Define users/roles, records, categories, budgets, bills, goals, insights, advice and ratings
  - [ ] Create indexed MySQL schema with decimal money fields and foreign keys
  - [ ] Add versioned migrations and seed only non-sensitive reference data
  - [ ] Test migrations and ownership constraints

- [ ] **T04 · Backend API foundation** · `ALL`
  - [ ] Create Spring Boot project with DTOs, validation and standard errors
  - [ ] Configure dev/test/prod settings, secrets and CORS policy
  - [ ] Add API versioning, OpenAPI contract, health check and test profile

### 01 — Authentication, consent and permissions

- [ ] **T05 · Secure authentication** · `FR01`
  - [ ] Implement register/login/logout, password hashing and expiring tokens
  - [ ] Validate credentials and rate-limit authentication attempts
  - [ ] Test invalid login, expired tokens and account disablement

- [ ] **T06 · Role-based access control** · `ROLE-CR`
  - [ ] Define USER, ADMIN and STAFF permissions, default deny
  - [ ] Enforce permissions on every API endpoint and service
  - [ ] Test user-to-user isolation, horizontal and vertical privilege escalation

- [ ] **T07 · Consent and account settings** · `FR01 / FR11` · with Sahan
  - [ ] Capture over-18 confirmation and informed consent with version/time
  - [ ] Implement profile language and account preferences
  - [ ] Provide consent withdrawal and data-deletion entry points

### 02 — User records and imports

- [ ] **T09 · Accounts and categories** · `FR02` · with Sahan
  - [ ] Build CRUD API and Android screens for accounts/categories
  - [ ] Validate currency LKR, user ownership and safe deletion
  - [ ] Test duplicates and invalid input

- [ ] **T10 · Income and expense records** · `FR02` · with Sahan
  - [ ] Create add/edit/delete/list endpoints and mobile forms
  - [ ] Store money as precise decimals with consistent dates/categories
  - [ ] Test totals, edits, filters and account isolation

- [ ] **T12 · CSV import** · `FR03` · with Sahan
  - [ ] Define CSV template, mapping, dates and amount validation
  - [ ] Preview/confirm imported records and flag duplicates
  - [ ] Reject malformed/oversized files and CSV formula-injection payloads

### 04 — Budgets, bills, goals and dashboard

- [ ] **T17 · Monthly budgets** · `FR04` · with Sahan
  - [ ] Implement budget CRUD and month/category limits
  - [ ] Calculate used/remaining amounts against confirmed transactions
  - [ ] Show overspend states and tests for boundary dates

- [ ] **T18 · Monthly bills** · `FR04` · with Sahan
  - [ ] Implement bill schedule, due dates and paid/unpaid state
  - [ ] Integrate bill totals into budget/planning calculations
  - [ ] Test month transitions and duplicate reminders

- [ ] **T19 · Savings goals** · `FR04` · with Sahan
  - [ ] Create goal CRUD, targets, dates and contributions
  - [ ] Compute progress correctly without double-counting
  - [ ] Test edits, deletions and invalid targets

### 07 — Planning, history, alerts and data rights

- [ ] **T33 · Monthly insights and score history** · `FR15` · with Sahan
  - [ ] Store monthly snapshots consistently and idempotently
  - [ ] Show prior scores and trends with clear dates
  - [ ] Test deletions and recalculation after corrections

- [ ] **T34 · Budget, bill and goal alerts** · `FR13 (Should)` · with Sahan
  - [ ] Create server notification events and device token management
  - [ ] Use FCM with privacy-safe lock-screen content and opt-out
  - [ ] Test duplicate delivery, permission refusal and notification expiry

- [ ] **T35 · Data download** · `FR11` · with Sahan
  - [ ] Export only authenticated user-owned data in a documented format
  - [ ] Use safe filenames/headers; test completeness and unauthorized requests
  - [ ] Remove temporary export files and never log sensitive content

- [ ] **T36 · Data deletion** · `FR11` · with Sahan
  - [ ] Implement confirmation, deletion of dependent records and token invalidation
  - [ ] Define backup-retention behavior before launch
  - [ ] Test deletion/re-registration and privacy audit trail without sensitive payloads

### 09 — Admin login and five functions (gated change)

- [ ] **T41 · Admin login/dashboard** · `ADMIN-01`
  - [ ] Build secure ADMIN-only login route and compact status dashboard
  - [ ] Reject all non-admin access including forged tokens
  - [ ] Do not display user financial contents

- [ ] **T42 · Admin manage user accounts** · `ADMIN-02`
  - [ ] List safe account-status fields and search by permitted identifiers
  - [ ] Enable/disable account with reason and audit event
  - [ ] Test that Admin cannot read or edit user transactions

- [ ] **T43 · Admin manage staff accounts** · `ADMIN-03`
  - [ ] Create/disable STAFF accounts with least-privilege permissions
  - [ ] Prevent staff creation of admins or self-escalation
  - [ ] Test revocation and expired sessions

- [ ] **T44 · Admin default categories** · `ADMIN-04 / FR12`
  - [ ] CRUD default income/expense categories without altering user-owned records
  - [ ] Validate name uniqueness and safe deactivation
  - [ ] Test unauthorized edits

- [ ] **T45 · Admin bank SMS formats** · `ADMIN-05 / FR12`
  - [ ] Manage parser format templates with versions and validation
  - [ ] Publish safe updates for supported banks without exposing real SMS data
  - [ ] Test rollout/rollback and parser regressions

### 10 — Staff login and four functions (gated change)

- [ ] **T46 · Staff login/dashboard** · `STAFF-01` · with Sahan
  - [ ] Build STAFF-only login and limited support dashboard
  - [ ] Ensure staff cannot access admin settings or private financial records
  - [ ] Test blocked endpoints and disabled accounts

- [ ] **T47 · Staff support requests** · `STAFF-02` · with Sahan
  - [ ] Build support ticket workflow with user-submitted redacted details
  - [ ] Allow status and response updates only on assigned/permitted tickets
  - [ ] Test ticket isolation and attachment sanitization

### 11 — Tests, hardening and release

- [ ] **T50 · Backend API test suite** · `ALL` · with Thilina
  - [ ] Add JUnit/Mockito integration tests for endpoints, validation and ownership
  - [ ] Run real MySQL-backed migration/regression suite
  - [ ] Attach actual passing command output and fix failures

- [ ] **T56 · Deployment and backups** · `RELEASE`
  - [ ] Harden Ubuntu server, MySQL access, HTTPS and environment secrets
  - [ ] Set scheduled encrypted backups and perform a restore drill
  - [ ] Deploy API, migrations and signed Android APK with rollback plan

- [ ] **T57 · End-to-end acceptance and handoff** · `ALL` · with All
  - [ ] Demonstrate user signup→record→analysis→advice→planning on a real device
  - [ ] Demonstrate Admin and Staff only if role change is approved
  - [ ] Review open defects and document only verified working features

---

## Supporting tasks

- [ ] **T08 · Three-role login routing** · `ROLE-CR` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T15 · Transaction preview and deduplication** · `FR03` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T20 · Dashboard and charts** · `FR05` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T27 · Server-only Groq integration** · `FR09` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)
- [ ] **T49 · Staff SMS import troubleshooting** · `STAFF-04` · lead: Sahan → see [Sahan's tracker](TRACKER_SAHAN.md)
- [ ] **T52 · Three-role authorization tests** · `ROLE-CR` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)
- [ ] **T53 · Privacy and security checks** · `NFR-SEC` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)

---

## Notes / blockers

- 
