# FINORA — Task Index

Find a task here, then open **only** its file and section. Status values: `todo` · `in-progress` · `review` · `done`. Update status here (only here).

| ID | Task | Lead | Support | Depends on | File | Status |
|---|---|---|---|---|---|---|
| T01 | Confirm implementation decisions | Sevindu | Sahan, Thilina | — | [sevindu/1-foundation.md](sevindu/1-foundation.md) | in-progress |
| T02 | Repository and build setup | Sevindu | — | T01 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T03 | Database design and migrations | Sevindu | — | T02 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T04 | Backend API foundation | Sevindu | — | T02 | [sevindu/1-foundation.md](sevindu/1-foundation.md) | done |
| T05 | Secure authentication | Sevindu | — | T03, T04 | [sevindu/2-auth.md](sevindu/2-auth.md) | done |
| T06 | Role-based access control | Sevindu | — | T05 | [sevindu/2-auth.md](sevindu/2-auth.md) | done |
| T07 | Consent and account settings | Sevindu | Sahan | T05, T37 | [sevindu/2-auth.md](sevindu/2-auth.md) | in-progress |
| T08 | Three-role login routing | Sahan | Sevindu | T06, T37 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | todo |
| T09 | Accounts and categories | Sevindu | Sahan | T03, T06, T37 | [sevindu/3-records.md](sevindu/3-records.md) | in-progress |
| T10 | Income and expense records | Sevindu | Sahan | T09 | [sevindu/3-records.md](sevindu/3-records.md) | in-progress |
| T11 | Manual transaction entry | Sahan | — | T10, T37 | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | todo |
| T12 | CSV import | Sevindu | Sahan | T10 | [sevindu/3-records.md](sevindu/3-records.md) | todo |
| T13 | SMS access and fallback | Sahan | — | T37 | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | todo |
| T14 | Local bank SMS parsing | Sahan | — | T13, T45.3 (can start with bundled formats) | [sahan/2-entry-sms.md](sahan/2-entry-sms.md) | todo |
| T15 | Transaction preview and deduplication | Sahan | Sevindu | T14, T10 | [sahan/3-sms-quality.md](sahan/3-sms-quality.md) | todo |
| T16 | SMS parser regression tests | Sahan | Thilina | T14 | [sahan/3-sms-quality.md](sahan/3-sms-quality.md) | todo |
| T17 | Monthly budgets | Sevindu | Sahan | T10 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | in-progress |
| T18 | Monthly bills | Sevindu | Sahan | T17 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | in-progress |
| T19 | Savings goals | Sevindu | Sahan | T10 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | in-progress |
| T20 | Dashboard and charts | Sahan | Sevindu | T10, T17 | [sahan/4-screens.md](sahan/4-screens.md) | in-progress |
| T21 | Deterministic spending metrics | Thilina | — | T10, T17, T18 | [thilina/1-metrics-patterns.md](thilina/1-metrics-patterns.md) | review |
| T22 | Spending-pattern detection | Thilina | — | T21 | [thilina/1-metrics-patterns.md](thilina/1-metrics-patterns.md) | review |
| T23 | Health-score model (0–100) | Thilina | — | T21, T18, T19, T01.3 | [thilina/2-score-rules.md](thilina/2-score-rules.md) | todo |
| T24 | Insights and health-score screens | Sahan | Thilina | T22, T23, T38 | [sahan/4-screens.md](sahan/4-screens.md) | todo |
| T25 | Rule-based advice engine | Thilina | — | T21, T22, T23 | [thilina/2-score-rules.md](thilina/2-score-rules.md) | todo |
| T26 | AI-safe fact and response schema | Thilina | — | T21, T25 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | todo |
| T27 | Server-only Groq integration | Thilina | Sevindu | T04, T26 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | todo |
| T28 | Gemini backup and rule fallback | Thilina | — | T27 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | todo |
| T29 | Number and claim verification | Thilina | — | T26, T27 | [thilina/3-ai-pipeline.md](thilina/3-ai-pipeline.md) | todo |
| T30 | Advice delivery and ratings | Sahan | Thilina | T25, T29, T38 | [sahan/4-screens.md](sahan/4-screens.md) | todo |
| T31 | Daily spending guide | Thilina | Sahan | T17, T18, T19 | [thilina/4-planning-tools.md](thilina/4-planning-tools.md) | todo |
| T32 | Savings goal simulator | Thilina | Sahan | T19 | [thilina/4-planning-tools.md](thilina/4-planning-tools.md) | todo |
| T33 | Monthly insights and score history | Sevindu | Sahan | T22, T23 | [sevindu/4-planning-data.md](sevindu/4-planning-data.md) | todo |
| T34 | Budget, bill and goal alerts | Sevindu | Sahan | T17, T18, T19, T37 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | todo |
| T35 | Data download | Sevindu | Sahan | T10, T17, T18, T19 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | todo |
| T36 | Data deletion | Sevindu | Sahan | T35 | [sevindu/5-alerts-data-rights.md](sevindu/5-alerts-data-rights.md) | todo |
| T37 | Mobile app skeleton | Sahan | — | T02 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | in-progress |
| T38 | Financial screens and navigation | Sahan | — | T37, T09 | [sahan/4-screens.md](sahan/4-screens.md) | todo |
| T39 | Sinhala and English support | Sahan | — | T37 | [sahan/1-app-skeleton.md](sahan/1-app-skeleton.md) | todo |
| T40 | Android privacy and resilience | Sahan | — | T37, T08 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | todo |
| T41 | Admin login and dashboard | Sevindu | — | T06, T08 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T42 | Admin manages user accounts | Sevindu | — | T41 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T43 | Admin manages staff accounts | Sevindu | — | T41 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T44 | Admin default categories | Sevindu | — | T41, T09 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T45 | Admin bank SMS formats | Sevindu | Sahan | T41, T14 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T46 | Staff login and dashboard | Sevindu | Sahan | T06, T08, T43 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T47 | Staff support requests | Sevindu | Sahan | T46 | [sevindu/6-admin-staff.md](sevindu/6-admin-staff.md) | todo |
| T48 | Staff application assistance | Sahan | — | T46, T47 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | todo |
| T49 | Staff SMS import troubleshooting | Sahan | Sevindu | T46, T45 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | todo |
| T50 | Backend API test suite | Sevindu | Thilina | T05 (grows with every API task) | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | review |
| T51 | Android functional tests | Sahan | Thilina | T38 | [sahan/5-privacy-staff-tests.md](sahan/5-privacy-staff-tests.md) | todo |
| T52 | Three-role authorization tests | Thilina | Sevindu | T06, T41, T46 | [thilina/5-qa.md](thilina/5-qa.md) | in-progress |
| T53 | Privacy and security checks | Thilina | Sevindu | T14, T27 | [thilina/5-qa.md](thilina/5-qa.md) | todo |
| T54 | AI and financial correctness tests | Thilina | — | T29 | [thilina/5-qa.md](thilina/5-qa.md) | todo |
| T55 | SMS, usability and performance checks | Thilina | Sahan | T16, T38 | [thilina/5-qa.md](thilina/5-qa.md) | todo |
| T56 | Deployment and backups | Sevindu | — | T04 | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | todo |
| T57 | End-to-end acceptance and handoff | Sevindu | Sahan, Thilina | all tasks | [sevindu/7-testing-release.md](sevindu/7-testing-release.md) | todo |

## Your parts inside other members' tasks

- **Sevindu:** T08, T15, T20, T27, T49, T52, T53 — look for subtasks tagged `[…·Sevindu]`
- **Sahan:** T01, T07, T09, T10, T12, T17, T18, T19, T31, T32, T33, T34, T35, T36, T45, T46, T47, T55, T57 — look for subtasks tagged `[…·Sahan]`
- **Thilina:** T01, T16, T24, T30, T50, T51, T57 — look for subtasks tagged `[…·Thilina]`

## Files per member

- **Sevindu:** 1-foundation · 2-auth · 3-records · 4-planning-data · 5-alerts-data-rights · 6-admin-staff · 7-testing-release
- **Sahan:** 1-app-skeleton · 2-entry-sms · 3-sms-quality · 4-screens · 5-privacy-staff-tests
- **Thilina:** 1-metrics-patterns · 2-score-rules · 3-ai-pipeline · 4-planning-tools · 5-qa
