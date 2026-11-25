# T55 evaluation protocol and evidence

2026-11-22. Owner: Thilina; execution with Sahan after T16/T38. The protocol below includes the measured D35 JVM result.
Handset latency and usability measurement remain pending.

## SMS fixture and evidence specification (T16/T55.1)

Use Sahan's actual supported bank-format catalogue; never invent a bank format. Cover debit, credit,
reversal, ATM, malformed, unsupported sender/format and ambiguous-date/manual-review cases where applicable.
Use consented, locally redacted fixtures or explicitly synthetic fixtures matching approved templates;
record provenance separately. Raw SMS and unredacted identifiers must not enter API traffic, reports or logs.

Local parser fixture manifest (owned by T16):
`fixtureId,formatId,provenance,localFixtureRef,expectedParseStatus,expectedAmountLkr,expectedDate,expectedType,expectedMerchantPlaceholder`.
No real account numbers, names or merchants in shared fixtures. Unknown/ambiguous cases must be manually reviewed,
not silently assigned invented amounts/dates. Unsupported reversal handling needs an explicit expected contract.

Shared result row (no SMS text):
`runId,commit,deviceOrJvm,fixtureId,formatId,field,applicable,correct,parseStatus,defectRef`.
Proposed calculation for lecturer/team confirmation: field accuracy = correct applicable fields / all applicable
expected fields * 100; report numerator/denominator per field and bank, plus manual-review and false-accept counts.
Failed known parses count as incorrect expected fields, not excluded successes. No denominator means unavailable.
T16/T55 target is >=90%; confirm aggregate weighting before making an acceptance claim. Do not tune fixtures after
seeing results without recording the change. Have a second reviewer check labels and discrepancies.

## Android/API integration checklist

- Record app/API commits, V1 build, device/OS, locale and test run ID; use synthetic accounts and finora_test only.
- Register/login; post confirmed manual transactions; verify dashboard/category totals, budget boundaries and goals.
- Confirm score inputs have explicit dated reserve and applicability; display unavailable reasons honestly.
- Confirm privacy during SMS preview/import: full text remains local; only approved confirmed fields leave the phone.
- Exercise offline, timeout, invalid JWT/refresh and disabled-account paths; English/Sinhala labels and reason keys.
- Advice HTTP delivery and simulator UI are prerequisite contracts, not implemented by the T54 service tests.
  Sahan owns Android wiring; Sevindu owns API contract approval. Record exact mismatches instead of changing UI silently.

## Screen performance (T55.2)

Seed a versioned synthetic 12-month dataset with recorded row counts, categories, bills and goals; retain the seed
procedure/commit. Use the dedicated persistent environment proposed below for the handset run. Record mid-range phone model/OS, build type, API host/hardware,
network and cache state. Measure user action until usable screen content (including rendering), separately from API latency.
Cover actual implemented main screens; distinguish cold start and warm navigation. Suggested repeatability protocol:
5 warm-up runs and 30 measured warm runs per screen, plus separately recorded cold runs; these counts are provisional
method choices, not FINORA thresholds. Keep raw timing rows and report median, p95, worst case and failures.
Approved target is screen time <2 seconds. Team/lecturer must confirm which statistic governs acceptance; do not
substitute an API-only measurement. Use existing Android tracing tools; no new executable installation is assumed.

Timing row: `runId,commit,device,screen,locale,coldOrWarm,network,elapsedMs,completed,errorRef`.

## Usability (T55.3) and advice evaluation (T55.4)

Use a lecturer-approved SUS questionnaire/scoring sheet, participant consent and anonymized participant IDs.
Use synthetic finances and a consistent task script: login, enter expense, inspect budget/score, add goal contribution,
understand missing-data/advice explanations. Observe completion, assistance, errors and comments without retaining PII.
Record recruitment, sample size, language, exclusions and missing answers; do not impute responses. Verify scoring
with a second reviewer. Target SUS >=70; report actual distribution and limitations, not a fabricated representative result.

Usability row: `runId,participantCode,locale,appCommit,task,completed,assistance,errorRef,questionnaireRef`.
Questionnaire/result sheets stay access-controlled; repository reports contain de-identified aggregates only.

D03 is V1-only; the old Beta comparison item needs team/lecturer clarification. Do not add a runtime mode or perform
a RULE-vs-AI statistical comparison without an approved study design, consent, verifier policy and authorized live calls.
Predefine outcome, unit of analysis, pairing, missing-data treatment and statistical method before collecting ratings.

## Results / blockers

| Requirement | Evidence | Status |
|---|---|---|
| SMS field accuracy >=90% | Execution 26C measured 12/12 on the approved synthetic NSB JVM corpus | Technical PASS; formal review pending |
| Main screens <2 seconds | T20 warm dashboard viewport: 505/504/486 ms with 27 synthetic records and USB capture overhead. Full T55.2 timing is pending. | Partial evidence |
| SUS >=70 | No participants/responses collected | Blocked |
| Advice comparison | D03/protocol and live-call approvals unresolved | Blocked |

For each future run attach run ID, commit/device details, denominator/raw result reference, observed result,
reviewer, limitations and defect/next action. Keep T55 incomplete until real evidence and review exist.

## Execution 27 evidence review (T55.1)

Execution 26C, 2026-11-25, integration baseline `baa6552c59d8f5737dd202ae5aa1f20b5d80c2bc`.
The baseline was committed at 09:13:21 UTC. The four suite timestamps span 09:21:25.731–09:21:25.819 UTC;
the execution log was last written at 09:21:26.882 UTC. The clean checkout matches the expected baseline,
and the log shows app and test Java compilation and the test task executing, with 24/24 tasks executed.
Neither XML nor the Gradle log embeds the Git SHA. The tested-commit attribution uses the Execution 26C
checkpoint context and timestamp/source reconciliation. No test rerun was used.

Windows Android V1 debug JVM task `:app:testV1DebugUnitTest`; Gradle 9.5.0 referenced by the log.
The current checkout declares Java 17, compile/target SDK 37 and min SDK 26. Local SDK platforms 34 and
37.0 and the Java 17.0.21.1 recovery configuration were inspected. The retained log provides no JVM binary,
SDK-root readback or handset details.

| Suite | Tests | Failures / errors / skipped | XML SHA-256 |
|---|---:|---|---|
| NsbSmsParserTest | 5 | 0 / 0 / 0 | e8bf2827bcc054c657509277256f4c7d6f43d400f8ef6ea832f193c89a9dae74 |
| SmsAccuracyTest | 2 | 0 / 0 / 0 | 82d871e211281e8eb9aa5af25ba56b8c9cdce1dfa37af49f01109b1e96249ccd |
| SmsParserTest | 8 | 0 / 0 / 0 | d84331e5d9ed044241f1a3c3c9d657025702a0cb59f51883aee38b1fd991503b |
| RemoteSmsFormatsTest | 4 | 0 / 0 / 0 | ca886642f29a84c0071fa5c96af8b97b169c097e8ff26d15595e9f74498eaf8b |

The XMLs are under `android/app/build/test-results/testV1DebugUnitTest/` (suite-qualified `TEST-*.xml`).
The execution log is `%LOCALAPPDATA%/Temp/finora-t55-recovery-801c1ea89b93487ca7f71483061b9177/android-sms-tests.log`
(SHA-256 `8141c4e59bb2216a7c580106d3172fdb2c30c150fb2cae8b07fecdada93222c6`).
These are local artifacts. Retain the originals separately for independent review.

```text
BUILD SUCCESSFUL in 45s
24 actionable tasks: 24 executed
D35 NSB field accuracy: 12/12 (100.0%) across 3 synthetic positive fixtures
D35 negative cases routed to manual review: 6/6
```

D35 approved the three formats. D38 approved the synthetic-only fixtures replacing customer and financial
details. Source identity at the baseline (Git blob IDs): `SmsAccuracyTest.java` `991dafa0a73be9b524cf76edec97ad4b69329f00`,
`nsb_synthetic_messages.txt` `2072b03b4c6a0690f40dd154052c10d79b93269e`, and bundled
`bank_formats.json` `744d202159747cc07a9660b98b5a85b15d64a321`.

| Approved format | Amount | Local date | Direction | Merchant | Fields correct |
|---|---|---|---|---|---:|
| POS debit | Correct | Correct | Debit | Expected synthetic value | 4/4 |
| Outward CEFT debit | Correct | Correct | Debit | Expected absent | 4/4 |
| Inward CEFT credit | Correct | Correct | Credit | Expected absent | 4/4 |
| Total | 3/3 | 3/3 | 3/3 | 3/3 | 12/12 |

The passing test prints the aggregate, and 12/12 implies all individual comparisons matched.
Each unique positive contributes four comparisons, including two expected null merchants. A failed parse
would leave all four fields incorrect. The test asserts the 90% floor. The negative test individually
asserts manual review for unknown text, malformed/truncated text, ambiguous amount, reversal, ATM cash
withdrawal and invalid date (6/6, zero false accepts on that set). Inbox/paste and bundled/seeded checks
reuse the approved formats, and remote-state and generic-parser regressions are separate from the denominator.
The scope is this synthetic corpus and local JVM. Independent fixture/denominator review and formal T16/T38
closure remain required. General bank coverage, sender authenticity and handset acceptance remain unverified.

## T55.2 proposed persistent evaluation environment

Request Sevindu's approval for provisioning and the write path, and Sahan's approval for screen markers,
test profile, packages and runner. Thilina coordinates the run and confirms device use with its owner.
Agree the screen set, sampling, failure treatment and <2 s acceptance statistic with the team/lecturer
before gathering measurements. T16 and T38 remain formal dependencies. The retained T20 handset result covers only warm Records-to-Home with 27 synthetic records across 12 months. See [T20 evidence](../trackers/sahan/4-screens.md).

1. Provision a dedicated loopback MySQL instance and schema (for example, `finora_t55_perf`), with synthetic-only
   contents, a schema-scoped account, explicit instance and schema checks, and Flyway reference data. Bind the
   dedicated API to laptop loopback port 8080. Fail preflight on a reused/unexpected schema, missing reference
   rows, unexpected account or fixture rows, or unsafe API configuration. Set `finora.llm.enabled=false`,
   supply no provider keys, and deny API external egress. Use neither the shared rollback test database nor a
   personal or production database.
2. Register two disposable USER identities through normal registration and consent. Generate per-run passwords
   outside source and enter through the in-process UI harness, keeping values out of command arguments, logs and
   Git. Keep normal password validation, session issuance and owner authorization. Add a manually invoked,
   test-source-only adapter after approval. Reuse the canonical `EvaluationDataset` blueprint and extract shared
   financial-row insertion from the test seeder only if it can preserve its guard, non-login fixture hashes and
   rollback tests. The adapter targets the separately allowlisted environment, validates the two expected
   USER owners and empty owner state, and loads the financial rows in one explicit transaction. Refuse duplicates,
   roll back on failure, and verify all row counts, profile totals, canonical checksum, normal login and
   two-owner isolation before handset measurements. No app startup hook, reset, delete or persistent run
   through the rollback integration test is required.
3. Rehearse on an isolated emulator. On the approved mid-range handset use V1 debug, built via
   `gradlew.bat assembleV1Debug assembleV1DebugAndroidTest -PfinoraHandsetAcceptance=true`, with
   `lk.finora.app.acceptance` and `lk.finora.app.acceptance.test`. Verify pinned commit, APK hashes,
   package IDs, signer, instrumentation target and guarded AndroidJUnitRunner. Preflight authorized ADB serial,
   existing active unlocked test profile, Android user ID and separate user serial, port ownership, focus
   and SMS permission ungranted. Bind operations to that serial/profile. Use a scoped ADB reverse
   `tcp:8080 tcp:8080` for the V1 debug loopback URL and prove synthetic-only connectivity before login.
   Leave personal packages and sessions untouched. D23 release HTTPS/trust and D48–D51 still apply.
4. Measure Home, Records/transactions, Plan/budgets/bills/goals and Insights/score, with More and other
   T38 destinations included as agreed. Use a fixed seeded month such as November 2026 and define a
   usable-content assertion for each screen (e.g. visible totals, list rows, chart/score or deliberate empty
   state; a spinner or error is a failure). Time from the action or process launch on a device-monotonic clock
   through the first rendered frame showing usable data. Use Android frame tracing or an approved instrumentation
   marker cross-checked against frame display; list readiness alone or Espresso idle time is insufficient.
   Record separately client HTTP request duration and server request duration under a sanitized correlation ID.
   Keep cold runs (process restarted, session/cache policy documented) separate from warm navigation (process
   and session retained). Record device/OS, build, locale, network, cache, API host, dataset/hash, run order,
   app/API revisions, action-to-frame time, HTTP/server times, and outcome for every attempt.
5. Proposed per screen/cohort: five uncounted warm-ups and 30 measured attempts in each cold/warm cohort
   where the same route can be reproduced. Define launch-inclusive cold deep-screen journeys separately.
   Report successful latency median, nearest-rank p95, maximum and counts of successes, failures, and
   timeouts; retain failed attempts in the trial totals. Preserve raw rows and mark missing timings.
   Thilina owns sanitized evidence and read-back of settings. Sahan/device owner oversees profile restoration
   and removal of only the temporary reverse mapping. Sevindu owns the dedicated instance lifecycle and
   any separately approved manual disposal after evidence retention.

## T55.3 moderator and participant materials (draft for approval)

### Participant information and consent

FINORA CSE5015 Group 9 is studying how easily intended users can complete common budgeting tasks.
Allow approximately 20–30 minutes, subject to the approved pilot. Use only the supplied test account and
invented data. You may skip tasks or survey answers and may stop without penalty. The moderator records
task outcome, assistance, errors, elapsed time and survey responses under a random participant code.
Possible inconvenience includes time and frustration using an unfamiliar app. Personal finances, bank records,
SMS and names are unnecessary. No recording is planned. The report will use de-identified aggregate results.

**Before recruitment:** Fill in approved researcher/lecturer contact, eligibility, retention/deletion date,
authorized data access, and withdrawal contact and deadline. Store any required signed consent separately
from observation records. Give the participant their random code for withdrawal before aggregation.

Consent checkboxes for an approved session:

- [ ] I read the participant information and had the opportunity to ask questions.
- [ ] I meet the approved eligibility criteria and understand participation is voluntary.
- [ ] I agree to the coded observation and questionnaire use described above.

Record consent outcome and protocol version before starting. A refusal ends the session before data collection.

### Standard task script

Use an approved clean synthetic participant account and the same fixture version, device settings and
locale per cohort. Explain the aim without demonstrating navigation. Give one task at a time, read
the same wording and log start/end and outcome. Ask participants to use the app as they normally would.

| Task to read | Moderator success criterion |
|---|---|
| Sign in using the supplied test-account details. | Signed-in Home for the assigned synthetic user. |
| Record the supplied LKR 500 food expense for the scenario date using the supplied wallet. | One matching saved expense in Records. |
| Find the scenario month's Shopping budget and explain how much is left or whether it has been exceeded. | Correct budget and explanation matching the fixture oracle. |
| Find the financial health score for the scenario month and explain what the app says about missing information. | Correct scenario score state and explanation from the app. |
| Add the supplied LKR 500 contribution to the active synthetic goal. | One saved contribution and matching updated progress. |
| Find the available advice and explain what it recommends. | Participant reaches the advice and describes the stated action or unavailable reason. Use provider-disabled isolated API. |

Use a pretested scenario card with dates, wallet, month, expected budget/score state and goal progress.
Confirm the moderator's oracle and app route in an approved pilot. Give one neutral prompt if requested
(“What would you try next?”), then a standardized task hint if needed. Record task-directed hints as
assistance. At the agreed time limit, present the next task using a prepared independent starting state.
Record environment failures separately from user errors, and preserve the original result for any retry.

### SUS administration and scoring

After tasks and before debriefing, administer the standard ten-item SUS form in its original order using
the original numbered statements and 1–5 response scale (1 Strongly disagree, 5 Strongly agree).
Use the [original SUS questionnaire in Brooke (1996)](https://hci-studies.org/methods-and-measures/downloads/SUS_Brooke1996.pdf).
Give a clean form without sample marks. Confirm any translated version with the team/lecturer before use.
Use one selectable response per item. Score complete valid forms by summing `response - 1` for odd
items and `5 - response` for even items, then multiply the sum by 2.5 for a 0–100 SUS score.
Treat blank, ambiguous, multiple-mark or out-of-range responses as missing if the participant does
not voluntarily resolve them before submission. Exclude incomplete forms from SUS scoring, preserve their
permitted task data, report exclusions and denominators, and have a second reviewer verify scores.

### Collection and de-identified reporting templates

Use random codes with no participant initials, dates of birth or contact information. Keep the study records
access-controlled and outside Git. If a contact or consent register is required, store it separately.
Use `unassisted / assisted / incomplete / skipped / technical failure` for outcomes and record neutral prompts
and hints separately. Record observed errors in coded terms and omit identifying comments.

- Observation row — run, participant code, protocol, locale, app/API commits, fixture version, task,
  outcome, neutral prompt count, hint level, elapsed time or missing reason, coded user error, technical incident.
- SUS row — participant code, protocol, locale, Q1–Q10 responses, valid item count, missing reason,
  score if all valid, independent score verifier.
- Aggregate report — eligibility and recruitment method; invited/consented/started/completed/withdrawn counts;
  usable and excluded SUS forms with reasons; score mean, median, range and sample size; target ≥70 using the
  statistic confirmed before collection; per-task unassisted, assisted, incomplete, skipped and technical counts
  with denominators; recurring issues, deviations and limitations. Avoid small subgroup disclosures.
- Team/lecturer approval record — approved revision, date, recruitment plan, languages, consent/contact,
  access/retention, time limits, SUS acceptance statistic, pilot and reviewer. No participant study or SUS
  score exists for this draft. D03 V1-only applies.
