# FINORA — Task Index

Find a task here, then open **only** its file and section. Status values: `todo` · `in-progress` · `review` · `done`. Update status here (only here).

The [criterion audit](../docs/sahan-final-audit.md) supports **81/84 verified** across MD01–MD05. The original delegated scope is 22/22 within its approved scope. T07 is 6/7. T13 is in review after the D50 handset grant. T39 has bilingual handset walkthrough and audible main-button evidence. Locale write safety and planning translation approval remain open. T40.1 was deferred under D51/D56/T56; the D59 HTTPS domain is now live, so its release-build check can be run. Formal prerequisites and independent reviews remain in force.

| ID | Task | Lead | Support | Depends on | File | Status |
|---|---|---|---|---|---|---|
| T01 | Confirm implementation decisions | Sevindu | Sahan, Thilina | — | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T02 | Repository and build setup | Sevindu | — | T01 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T03 | Database design and migrations | Sevindu | — | T02 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T04 | Backend API foundation | Sevindu | — | T02 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T05 | Secure authentication | Sevindu | — | T03, T04 | [sevindu/2-auth.md](sevindu/2-auth.md) | done |
| T06 | Role-based access control | Sevindu | — | T05 | [sevindu/2-auth.md](sevindu/2-auth.md) | done |
| T07 | Consent and account settings | Sevindu | Sahan | T05, T37 | [sevindu/2-auth.md](sevindu/2-auth.md) | review |
| T08 | Three-role login routing | Sahan | Sevindu | T06, T37 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | done |
| T09 | Accounts and categories | Sevindu | Sahan | T03, T06, T37 | [sevindu/3-records.md](sevindu/3-records.md) | done |
| T10 | Income and expense records | Sevindu | Sahan | T09 | [sevindu/3-records.md](sevindu/3-records.md) | review |
| T11 | Manual transaction entry | Sahan | — | T10, T37 | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | review |
| T12 | CSV import | Sevindu | Sahan | T10 | [sevindu/3-records.md](sevindu/3-records.md) | review |
| T13 | SMS access and fallback | Sahan | — | T37 | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | done |
| T14 | Local bank SMS parsing | Sahan | — | T13, T45.3 (can start with bundled formats) | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | review |
| T15 | Transaction preview and deduplication | Sahan | Sevindu | T14, T10 | [sahan/3-sms-quality.md](sahan/3-sms-quality.md) | review |
| T16 | SMS parser regression tests | Sahan | Thilina | T14 | [sahan/3-sms-quality.md](sahan/3-sms-quality.md) | review |
| T17 | Monthly budgets | Sevindu | Sahan | T10 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | done |
| T18 | Monthly bills | Sevindu | Sahan | T17 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | done |
| T19 | Savings goals | Sevindu | Sahan | T10 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | done |
| T20 | Dashboard and charts | Sahan | Sevindu | T10, T17 | [sahan/4-screens.md](sahan/4-screens.md) | review |
| T21 | Deterministic spending metrics | Thilina | — | T10, T17, T18 | [thilina/1-metrics-patterns.md](thilina/1-metrics-patterns.md) | done |
| T22 | Spending-pattern detection | Thilina | — | T21 | [thilina/1-metrics-patterns.md](thilina/1-metrics-patterns.md) | done |
| T23 | Health-score model (0–100) | Thilina | — | T21, T18, T19, T01.3 | [thilina/2-score-rules.md](thilina/2-score-rules.md) | in-progress |
| T24 | Insights and health-score screens | Sahan | Thilina | T22, T23, T38 | [sahan/4-screens.md](sahan/4-screens.md) | review |
| T25 | Rule-based advice engine | Thilina | — | T21, T22, T23 | [thilina/2-score-rules.md](thilina/2-score-rules.md) | review |
| T26 | AI-safe fact and response schema | Thilina | — | T21, T25 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | review |
| T27 | Server-only Groq integration | Thilina | Sevindu | T04, T26 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | review |
| T28 | Gemini backup and rule fallback | Thilina | — | T27 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | review |
| T29 | Number and claim verification | Thilina | — | T26, T27 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | review |
| T30 | Advice delivery and ratings | Sahan | Thilina | T25, T29, T38 | [sahan/4-screens.md](sahan/4-screens.md) | review |
| T31 | Daily spending guide | Thilina | Sahan | T17, T18, T19 | [thilina/4-planning-tools.md](thilina/4-planning-tools.md) | in-progress |
| T32 | Savings goal simulator | Thilina | Sahan | T19 | [thilina/4-planning-tools.md](thilina/4-planning-tools.md) | in-progress |
| T33 | Monthly insights and score history | Sevindu | Sahan | T22, T23 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | review |
| T34 | Budget, bill and goal alerts | Sevindu | Sahan | T17, T18, T19, T37 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | done |
| T35 | Data download | Sevindu | Sahan | T10, T17, T18, T19 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | review |
| T36 | Data deletion | Sevindu | Sahan | T35 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | review |
| T37 | Mobile app skeleton | Sahan | — | T02 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | done |
| T38 | Financial screens and navigation | Sahan | — | T37, T09 | [sahan/4-screens.md](sahan/4-screens.md) | review |
| T39 | Sinhala and English support | Sahan | — | T37 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | in-progress |
| T40 | Android privacy and resilience | Sahan | — | T37, T08 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | in-progress |
| T41 | Admin login and dashboard | Sevindu | — | T06, T08 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | done |
| T42 | Admin manages user accounts | Sevindu | — | T41 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | review |
| T43 | Admin manages staff accounts | Sevindu | — | T41 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | done |
| T44 | Admin default categories | Sevindu | — | T41, T09 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | review |
| T45 | Admin bank SMS formats | Sevindu | Sahan | T41, T14 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | review |
| T46 | Staff login and dashboard | Sevindu | Sahan | T06, T08, T43 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | review |
| T47 | Staff support requests | Sevindu | Sahan | T46 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | review |
| T48 | Staff application assistance | Sahan | — | T46, T47 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | review |
| T49 | Staff SMS import troubleshooting | Sahan | Sevindu | T46, T45 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | review |
| T50 | Backend API test suite | Sevindu | Thilina | T05 (grows with every API task) | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | done |
| T51 | Android functional tests | Sahan | Thilina | T38 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | review |
| T52 | Three-role authorization tests | Thilina | Sevindu | T06, T41, T46 | [thilina/5-qa.md](thilina/5-qa.md) | in-progress |
| T53 | Privacy and security checks | Thilina | Sevindu | T14, T27 | [thilina/5-qa.md](thilina/5-qa.md) | in-progress |
| T54 | AI and financial correctness tests | Thilina | — | T29 | [thilina/5-qa.md](thilina/5-qa.md) | in-progress |
| T55 | SMS, usability and performance checks | Thilina | Sahan | T16, T38 | [thilina/5-qa.md](thilina/5-qa.md) | in-progress |
| T56 | Deployment and backups | Sevindu | — | T04 | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | review |
| T57 | End-to-end acceptance and handoff | Sevindu | Sahan, Thilina | all tasks | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | todo |
| T58 | Modern Material 3 UI | Sahan | — | T37 (D61) | [sahan/6-ui-redesign.md](sahan/6-ui-redesign.md) | in-progress |
| T59 | Admin creates admin accounts | Sevindu | Thilina, Sahan | T41, T43 | [sevindu/8-admin-accounts.md](sevindu/8-admin-accounts.md) | in-progress |
| T60 | Promote a USER to STAFF | Sevindu | Thilina, Sahan | T36, T42, T43 | [sevindu/8-admin-accounts.md](sevindu/8-admin-accounts.md) | todo |
| T61 | Forgot password by emailed code | Thilina | Sevindu, Sahan | T05 | [thilina/6-password-reset.md](thilina/6-password-reset.md) | in-progress |

## Your parts inside other members' tasks

See the [Sahan responsibility matrix](../docs/sahan-responsibility-matrix.md) for the 16 lead tasks, 19 explicit support tasks, overlapping delegated scopes and adjacent QA/deployment contributions. T01 is in review after its five technical checks. Ownership and independent approval remain unchanged.

MD04 delivery retains 20/20 technical checks and 5/5 for delegated T33 separately. T20, T24, T30, T33 and T38 remain in progress under the dependencies above. T39 remains in progress. See the [closure evidence](../docs/md04-authorized-closure.md) for independent review and live-provider gates. D40 and D41 permit scoped early implementation.

T53 verification update (2026-11-22): remains **in-progress**. Final upstream-compatible offline Java 17 + isolated finora_test Maven verify passed 206/206 tests (0 failures/errors/skips, exit 0); service coverage 668/677 lines (98.67%), JaCoCo gate passed. Earlier branch-only result was 151/151. Deployed TLS, device/provider captures, comprehensive scans and acceptance remain open; details in the T53 handoff. No other task statuses changed.

D58 advice update (2026-11-25): goal-coaching sections with checked free wording and a user-selected focus goal changed T25 (T25.4), T26 (T26.5), T29 (T29.4) and T30 (T30.6). T25, T26, T29 and T30 are in progress until Thilina reviews the D58 formulas and unsafe-phrase list.

Tracker sync (2026-11-27): 27 tasks with every subtask and Done-when box ticked moved from in-progress to **review** (T10–T12, T14–T16, T20, T24, T26–T30, T33, T35, T36, T38, T41–T49, T51). They become `done` only after human review. T32.1 and its tests are checked; T31 stays unchecked until the failing daily-guide test is resolved (see T31 handoff).

T25 review reconciliation (2026-11-27): all four rule-advice subtasks and the Done-when test are checked, including the D58 formula review recorded in merged PR #32. Move T25 to `review` under the same all-boxes-checked rule above. T23.6 and independent acceptance remain open; this is not a dependency waiver or production AI semantic amendment.

Tracker sync (2026-11-26): T31, T32, T54 and T55 moved from todo to **in-progress**. Daily-guide and simulator backend code (`api:planning/`), the T54 guarded provider evaluation and the T55 dataset, emulator timing and SUS tooling are on `main`. Their subtask boxes stay unchecked pending approvals, live runs and handset measurements.

- **Sevindu:** T61 (T61.5 server mail), T08, T15, T20, T27, T49, T52, T53 — look for subtasks tagged `[…·Sevindu]`
- **Sahan:** T59.3, T60.3, T61.4 (Android screens), T01, T07, T09, T10, T12, T17, T18, T19, T31, T32, T33, T34, T35, T36, T45, T46, T47, T55, T57 — look for subtasks tagged `[…·Sahan]`
- **Thilina:** T59.4, T60.4 (role/privacy tests), T01, T16, T24, T30, T50, T51, T57 — look for subtasks tagged `[…·Thilina]`

## Files per member

- **Sevindu:** 1-foundation · 2-auth · 3-records · 4-planning-data · 5-alerts-data-rights · 6-admin-staff · 7-testing-release · 8-admin-accounts
- **Sahan:** 1-app-skeleton · 2-entry-sms · 3-sms-quality · 4-screens · 5-privacy-staff-tests · 6-ui-redesign
- **Thilina:** 1-metrics-patterns · 2-score-rules · 3-ai-pipeline · 4-planning-tools · 5-qa · 6-password-reset
