# Thilina's engineering handoff and T57 acceptance evidence

27 November 2026. Status uses the [task index](../trackers/INDEX.md) at upstream `ae25ecdabdc5b68823900cd2eee230054a19484a`. PRs [30](https://github.com/SEVINTINE7/FINORA/pull/30), [31](https://github.com/SEVINTINE7/FINORA/pull/31) and [32](https://github.com/SEVINTINE7/FINORA/pull/32) have merged. The synchronized FINORA checkout was older at `e53b8e5`. Each status below belongs to its task owner.

## Lead tasks

| Task and official status | Implemented and verified criterion | Remaining acceptance |
|---|---|---|
| T21 · done | Monthly income, expense, needs/wants, savings, bills and spending windows with source counts and periods, null denominator and year-boundary payday checks. PRs #5/#22. [Metrics](../trackers/thilina/1-metrics-patterns.md) | Closed in index. |
| T22 · done | D10 high wants, payday, weekend and fixed-bill patterns, stored facts and USER API. Hand-calculated threshold, below-threshold, extreme and empty months. [Patterns](../trackers/thilina/1-metrics-patterns.md) | Closed in index. |
| T23 · in-progress | D20 five-part score, bounded points, missing and N/A inputs, owner declarations, stable reasons and D55 immutable category semantics. PRs #6/#17. [Score](../trackers/thilina/2-score-rules.md) | Team review and T18/T19 acceptance under T23.6. |
| T25 · in-progress | Six canonical actions and five eligible D58 sections, fixed-ledger budget invariance, rounding caps, shortfall and supported salary conclusions. PRs #19/#26/#32. [Financial oracles](d58-realistic-qualification.md) | Team and dependency acceptance. |
| T26 · review | Aggregate whitelist, D58 fact keys/units and built-in labels, strict output shape and V1 DTO compatibility. PRs #19/#26. [Schema](ai-schema.md) | Independent review. |
| T27 · review | Server Groq adapter, explicit model, bounded transport, metadata-only logs. Mocked failures and local HTTP serialization. PRs #8/#19. [Local privacy evidence](t53-local-security-evidence.md) | Independent review. Real-service evidence is scoped under T54. |
| T28 · review | Ordered Gemini backup for transport failure, exact canonical fallback on verifier rejection or exhausted providers. PRs #8/#19. [Pipeline](../trackers/thilina/3-ai-pipeline.md) | Independent review and separate live-service gates. |
| T29 · review | Exact eligible actions, amounts, sign, fact keys/units/periods/order, prohibited phrases, omitted sections and whole-response fallback. PRs #22/#26/#29/#30. [Verifier](../trackers/thilina/3-ai-pipeline.md) | D58 still permits free wording. Eleven synthetic contradictions exposed its semantic limit. Structured choice adoption and bilingual review remain team decisions. |
| T31 · in-progress | Daily allowance, unpaid-bill aggregate confirmation, savings, income date and assumptions, zero floor, negative and missing-data warnings. PRs #23/#28/#30. Android guide and Retry/rotation shipped to main. [API contract](t31-daily-guide-contract.md) | Sahan reviews T31.3 and team accepts the integrated journey. |
| T32 · in-progress | Owner-scoped hypothetical goal adjustments and cuts, financial rounding, infeasibility and no stored mutation. PR #30 integrated the Android simulator. [Planning](../trackers/thilina/4-planning-tools.md) | Sahan reviews T32.2 and team accepts. |
| T52 · in-progress | PR #32 verified 106 mappings, including 98 protected and eight auth, foreign IDs, privileged response privacy and CI. New characterization covers proposer/unrelated STAFF for DRAFT/ACTIVE plus repeat refusal and stored-row integrity. [Route evidence](t52-route-evidence.md) | Sevindu owns intended retirement actor/state policy. D64 adds two public routes on its own review revision. |
| T53 · in-progress | BCrypt, issued token lifetimes, refresh rotation, trusted public HTTPS, six captured synthetic SMS requests and both provider builders. PRs #19/#20/#21/#25/#31/#32. [Checklist](security-checklist.md) | Operator authorization, consented staging accounts and IDs, genuine expired token, runtime provider/Firebase and log attestations. Compatible dependency remediation, trusted fresh OWASP data and independent review remain. |
| T54 · in-progress | Hand-checked fixtures, isolated strict-stop adapters and fallback. One historical Gemini 503, zero assessable numerical replies, one required fallback verified, nine candidates skipped. Transport 2,337 ms and pipeline 2,355 ms. [Evaluation](t54-provider-evaluation.md) | Owner must establish exact revision/model scope, current access, quota and billing protection; deployment of structured coaching needs a policy decision. |
| T55 · in-progress | SMS accuracy 12/12 fields; validated 826-transaction benchmark. 45 submission observations, eight qualified follow-up presentations and two unqualified. SUS tools and protocol in PRs #20/#24. [Evaluation](evaluation.md) | Device owner/team approval of handset, profile and statistic. Academic approval, genuine observations and agreed analysis for SUS and advice comparison. |

## Supporting responsibilities

| Task and official status | Thilina's contribution and evidence | Remaining owner |
|---|---|---|
| T01 · done | Agreed thresholds, five score formulas and local setup. [D10/D20 handoff](../trackers/sevindu/1-foundation.md) | Closed by Sevindu. |
| T16 · review | D35/D38 expected fields for three NSB message formats, 12/12 fields and unsupported-input handling. [Parser evidence](../trackers/sahan/3-sms-quality.md) | Sahan and independent reviewer. |
| T24 · review | Score components/reasons, pattern facts and fixtures for bilingual Insights. [Screen handoff](../trackers/sahan/4-screens.md), [emulator journeys](evaluation.md) | Sahan and independent reviewer. |
| T30 · review | Verified V1 advice or rules, once-only rating 1–5 and owner-scoped frozen focus-goal delivery. PRs #19/#26. [Delivery](../trackers/sahan/4-screens.md) | Sahan and independent reviewer. |
| T50 · done | MySQL controller, permissions/ownership and CI service coverage checks. PR #32 hosted 506 passed, zero failures/errors, one intentional skip. [Backend tracker](../trackers/sevindu/7-testing-release.md) | Closed by Sevindu. |
| T51 · review | Provided app fixture values, compact-device and account-switching checks, four connected planning/import journeys on PR #30. [App evidence](d58-realistic-qualification.md), [Sahan's platform evidence](../trackers/sahan/5-privacy-staff-tests.md) | Sahan and independent reviewer. |
| T57 · todo | T57.4 verified flow segments and limitations below. [Group release criteria](../trackers/sevindu/7-testing-release.md), [MD05 evidence](md05-delivery-handoff.md) | Sevindu owns full real-phone chain, five ADMIN/four STAFF functions and final signoff with Sahan. Reconcile historical beta wording with D03 V1-only. |

## Newly assigned responsibilities

| Task at upstream baseline | Work and independent dependency |
|---|---|
| T61 · todo · lead | D64 public reset request and confirmation, localized code email, rolling rate limit, expiry, single use, attempt lockout and refresh revocation. Separate review branch `qa/t61-password-reset`; see its [tracker](../trackers/thilina/6-password-reset.md) for actual backend test results. Sahan owns Android recovery and Sevindu owns SMTP selection, server setup and receipt of the live email. |
| T59 · todo · support | T59.4 covers anonymous, USER, STAFF, ADMIN and disabled callers on create/list/enable-disable ADMIN. T41/T43 are now done. Sevindu's routes are absent at the cited revision. Exercise them when available. [Contract](../trackers/sevindu/8-admin-accounts.md) |
| T60 · todo · support | T60.4 requires the promoted owner to have zero financial rows and lose financial access, sessions to be revoked, and the other owner's data to remain intact. The promotion operation is absent. Sevindu owns it; T36/T42 remain in review. [Contract](../trackers/sevindu/8-admin-accounts.md) |

### Subsequent verified evidence — 2026-11-27

The above task statuses are historical. Upstream `5948f1dbe75bff114801465ad4f14234c1dabf8f` contains T59.1–T59.2. The prospective merge of [PR #34](https://github.com/SEVINTINE7/FINORA/pull/34) at `9c6ad9bd1142dbc7805a0bb33336f48e99b3ae41` passed both hosted jobs. The [API job log](https://github.com/SEVINTINE7/FINORA/actions/runs/45969996509/job/140407825126) records 527 tests run, 0 failures, 0 errors, 1 intentional skip; T52_RUNTIME total=111 protected=101 auth=10; EndpointAuthTest 7/7 and AdminAccountControllerTest 6/6.

- **T59.4:** The dynamic authorization inventory explicitly includes GET and POST `/api/v1/admin/admins` and PATCH `/api/v1/admin/admins/{id}`. Existing matrix tests cover anonymous, wrong-role, expired/malformed and disabled sessions; ADMIN controller tests cover permitted operations and denial, credential privacy and session revocation. This is evidence for the assigned role-matrix criterion, not closure of the parent task. Credit the feature and its controller tests to their original contributors. Reconcile the T59.4 tracker box when adopting this handoff.
- **T61.1–T61.3:** The published backend candidate has successful local and hosted verification. No real SMTP or signed Android recovery journey is established. Independent review, T61.4 and T61.5 remain open.
- **T60.4:** The required promotion endpoint is not present in the inspected upstream revision. Keep the privacy test obligation open; use disposable synthetic accounts when the implementation becomes available.

No new provider calls, deployed requests or participant evidence are implied by these CI observations. No new production policy is adopted.

The T25 checklist on upstream is fully checked, so this handoff proposes the ordinary `review` status, with T23 and independent acceptance retained. T18/T19 are officially `done`; T23.6 now waits for independent review. T31/T32 implementation is merged, and their Android support criteria remain open for Sahan's review. PR #33's own hosted verification at `5251b1a` confirms T59.4: 521 passed, 1 skipped, 109 mappings (101 protected/8 auth), `EndpointAuthTest` 7/7, `AdminAccountControllerTest` 6/6. See the [actual API job](https://github.com/SEVINTINE7/FINORA/actions/runs/45973464721/job/140419302365). The 111-route inventory above is the separate D64 prospective merge, not a contradiction.

## Demonstrated flows and remaining decisions

- Five realistic D58 accounts authenticated and passed their financial oracles on V24. The worker and laptop scenarios also passed ten Android UI checkpoints. The worker can transfer LKR 17,500 per month for six months under the fixture assumptions. The laptop plan supports LKR 4,000 per month, giving 23 months. Freelancer, shortfall and completed-goal cases have API evidence only. [Fixture data and APK identities](d58-realistic-qualification.md)
- The dedicated emulator passed four planning/import journeys on `264c1ae`. The D59 operator recorded signed-release invalid login and 34 restored table counts. The separate disposable local recovery verified both logins, totals and a foreign wallet 404. Authenticated staging and T57.1 physical end-to-end execution are still outstanding. [Security checklist](security-checklist.md)
- The separately published [Sinhala structured candidate](https://github.com/ThilinaW-LK/FINORA/commit/a3aa193d26445d4864703c8dc1975513abc40aba) preserves deterministic figures and warnings. Adoption needs a defined set of AI choices, required sections and warnings, server-owned EN/SI wording, native review, and whole-response fallback. The [offline handset manifest guard and timing joins](https://github.com/ThilinaW-LK/FINORA/commit/ab492c22228706b7aa1ca9caee7e9977c36124d4) confer no device permission. The emulator evidence cannot close T55.2 or T57.1.
- The isolated historical single-send revision `f043d0b61934f1f2da387df55c2069cb6c78dcdc` and D58 offline adapter revision `cfe6d5a5cb239796d1a207014c32b77789c2c177` remain preserved locally. The historical isolated no-focus protocol has its own authorization scope. Neither that protocol nor offline D58 results establish live model accuracy. The ceiling remains ten application sends and LKR 0.
- AGP experiment `59801b7` removed three of eleven build-classpath advisory matches; eight remained, including one critical. The assessed runtime graphs had zero matches. KGP experiment `81e8170` failed configuration. Both remain isolated. Supported remediation and trusted fresh OWASP data remain security acceptance requirements.

No new physical-handset, participant, deployed authentication or extended-usage observations are claimed. The frozen benchmark remains identified by SHA-256 `14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944`.

## PR #39 candidate verification

Two isolated FINORA emulator journeys passed. ADMIN creation, sign-in, disable, typed promotion and STAFF sign-in were observed. Password recovery opened in English and Sinhala and exercised the generic code request and invalid-code rejection. The backend synthetic tests verify promotion of only the intended owner, no response finance payload, session revocation and preservation of the second owner's finances. The new verification supplements the retained PR #30 planning evidence and PR #38 handset calibration. T57 independent sign-off, full signed-release journey, live reset delivery and formal T55 cohort remain outstanding.
