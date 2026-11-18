# FINORA — Sahan's Tracker

**Role:** Android / UI / SMS  
**Lead tasks:** 16 (48 subtasks) · **Supporting tasks:** 18  
**Master list:** [FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md](../FINORA_IMPLEMENTATION_ONLY_LEAN_TRACKER.md)

**Rules:** Tick a box only after the code is reviewed and real build/test output exists. Lead = you own delivery and tick the boxes here. Supporting = help the lead; boxes are ticked in the lead's tracker.

---

## Lead tasks

### 01 — Authentication, consent and permissions

- [ ] **T08 · Three-role login routing** · `ROLE-CR` · with Sevindu
  - [ ] Route authenticated users to their permitted dashboard
  - [ ] Hide unauthorized screens and actions but enforce security on server
  - [ ] Test role switch, session expiry and disabled accounts

### 02 — User records and imports

- [ ] **T11 · Manual transaction entry** · `FR02 / FR03`
  - [ ] Make offline-permission-independent manual entry available
  - [ ] Add required-field validation and user confirmation
  - [ ] Show clear import/entry errors without losing entered values

### 03 — Bank SMS import

- [ ] **T13 · SMS access and fallback** · `FR03`
  - [ ] Check current Android permission/distribution restrictions on real devices
  - [ ] Request permission only with clear explanation and refusal path
  - [ ] Offer paste/CSV/manual options without SMS permission

- [ ] **T14 · Local bank SMS parsing** · `FR03`
  - [ ] Create local SMS parser for supported bank formats
  - [ ] Extract amount/date/merchant where available; handle unknown formats
  - [ ] Never upload full SMS text; send only user-confirmed minimum fields

- [ ] **T15 · Transaction preview and deduplication** · `FR03` · with Sevindu
  - [ ] Display extracted details for user confirmation/editing
  - [ ] Detect duplicate SMS, repeated import and manual duplicates
  - [ ] Save only confirmed records; test failed sync and retry

- [ ] **T16 · SMS parser regression tests** · `FR03 / NFR-SMS` · with Thilina
  - [ ] Build consented/redacted SMS test dataset with bank-format coverage
  - [ ] Measure field-level accuracy against the ≥90% target
  - [ ] Test malformed, reversal, credit/debit and permission-denied cases

### 04 — Budgets, bills, goals and dashboard

- [ ] **T20 · Dashboard and charts** · `FR05` · with Sevindu
  - [ ] Build totals and category trend APIs with clear date filters
  - [ ] Create Android dashboard/charts and empty states
  - [ ] Reconcile chart totals against raw records and manual calculations

### 05 — Analytics and health score

- [ ] **T24 · Insights and score screens** · `FR06 / FR07` · with Thilina
  - [ ] Display evidence behind insights and every score component
  - [ ] Add clear explanations and not-financial-advice wording where appropriate
  - [ ] Verify mobile results match backend test fixtures

### 06 — Rule-based and verified AI advice

- [ ] **T30 · Advice delivery and ratings** · `FR09 / FR10` · with Thilina
  - [ ] Display checked AI or fallback advice with readable source context
  - [ ] Add 1–5 advice rating UI and rating storage
  - [ ] Test privacy, duplicate ratings and honest AI/rules provenance

### 08 — Android app experience

- [ ] **T37 · Mobile app skeleton** · `ALL`
  - [ ] Set up Android Java app, navigation and dependency injection/repository boundary
  - [ ] Configure secure HTTPS API client and authentication expiry handling
  - [ ] Add loading/empty/error states and real-device smoke test

- [ ] **T38 · Financial screens and navigation** · `FR02–FR15`
  - [ ] Connect records, import, budgets, dashboard and goal screens to API
  - [ ] Connect insights, health score, advice, history and settings screens
  - [ ] Test screen-state consistency after create/update/delete

- [ ] **T39 · Sinhala and English support** · `FR01`
  - [ ] Move all user-visible strings to localized resources
  - [ ] Review Sinhala wording with a native speaker
  - [ ] Test long labels, accessibility and language switching

- [ ] **T40 · Android privacy and resilience** · `NFR-SEC`
  - [ ] Keep tokens secure; no model/API keys or full SMS in logs
  - [ ] Minimize local storage and protect sensitive backups/screens
  - [ ] Test background, network loss, rotation and expired-session behaviour

### 10 — Staff login and four functions (gated change)

- [ ] **T48 · Staff application assistance** · `STAFF-03`
  - [ ] Provide non-sensitive help for login, navigation and common app issues
  - [ ] Create reusable help responses without requesting bank passwords or SMS text
  - [ ] Test visibility and permission limits

- [ ] **T49 · Staff SMS import troubleshooting** · `STAFF-04` · with Sevindu
  - [ ] Accept sanitized error codes and format mismatch reports only
  - [ ] Let staff flag issues for Admin without editing parser templates
  - [ ] Test that raw SMS, account identifiers and user finances are inaccessible

### 11 — Tests, hardening and release

- [ ] **T51 · Android functional tests** · `ALL` · with Thilina
  - [ ] Add UI tests for registration, records, import and main features
  - [ ] Test Android 8.0+ and recent devices with permission denied
  - [ ] Capture actual screenshots/test logs, never fabricated evidence

---

## Supporting tasks

- [ ] **T01 · Confirm implementation decisions** · `BASE` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T07 · Consent and account settings** · `FR01 / FR11` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T09 · Accounts and categories** · `FR02` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T10 · Income and expense records** · `FR02` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T12 · CSV import** · `FR03` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T17 · Monthly budgets** · `FR04` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T18 · Monthly bills** · `FR04` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T19 · Savings goals** · `FR04` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T31 · Daily spending guide** · `FR14` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)
- [ ] **T32 · Savings goal simulator** · `FR14` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)
- [ ] **T33 · Monthly insights and score history** · `FR15` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T34 · Budget, bill and goal alerts** · `FR13 (Should)` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T35 · Data download** · `FR11` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T36 · Data deletion** · `FR11` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T46 · Staff login/dashboard** · `STAFF-01` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T47 · Staff support requests** · `STAFF-02` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)
- [ ] **T55 · SMS, usability and performance checks** · `NFR-SMS / NFR-USE / NFR-PERF` · lead: Thilina → see [Thilina's tracker](TRACKER_THILINA.md)
- [ ] **T57 · End-to-end acceptance and handoff** · `ALL` · lead: Sevindu → see [Sevindu's tracker](TRACKER_SEVINDU.md)

---

## Notes / blockers

- 
