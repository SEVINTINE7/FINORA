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

### Execution 38 loopback preflight and frame probe

The Android and API production sources matched upstream 5d99285077e192c3749a175e67e2b664b90e8e0b.
The API ran from c496947e7b00302031661f7c356f4494e03f92d4 on 127.0.0.1:8080 against the retained
finora_t55_perf MySQL instance. Both existing users logged in. November income/expense were
60000/40000 LKR (worker) and 25750/11000 LKR (irregular). Both cross-owner goal reads returned
HTTP 404. Provider calls were disabled with credentials cleared. The JVM HTTP(S) proxy pointed at
a closed loopback port for external hosts. Host-level process network isolation was not tested.
The session API was stopped. A fresh database read reproduced all fixture counts and the original
transaction digest. The loader was not invoked.

The test-only RenderedContentProbe records the action boundary, the first pre-draw pass that satisfies a
caller-provided screen oracle, and the hardware frame-commit callback using elapsedRealtimeNanos.
It requires API 29 and hardware acceleration, returns an explicit timeout, and removes its callbacks after
collection. The callback reports submission to the compositor. Before counting pilot samples, corroborate
the marker with an actual displayed frame trace. Keep HTTP and server timing separate and correlate only
sanitized route and timing metadata.

Use November 2026 for all four oracles. For Home, match the owner-specific income/expense cards. For
Records, require the November transaction list to render a known fixture row for that owner. For Plan,
follow the Budgets route and require the November Shopping budget and its owner-specific limit. For
Insights, require the November score or its explicit fixture-defined missing-data state. Tab headings,
CTA buttons, spinners and network completions are insufficient. These oracles still need to be wired into
the connected rehearsal, and the cold and warm sample counts are both zero.

Gradle 9.5.0 and Java 17 built both V1 debug acceptance APKs, including androidTest Java
compilation. The application is lk.finora.app.acceptance and declares READ_SMS. The instrumentation
is lk.finora.app.acceptance.test, targets the acceptance application, and uses AndroidJUnitRunner.
APK SHA-256 values are 61566a20e6c82165d0dd4dfea85021f44d0dd48d39e0ca968736f51774eb62ed
(app) and 07e513f1e39ed74997766efaec642bb4ef9a33e6e2f3193b25626757611f206b
(instrumentation). The matching signer SHA-256 is
863fd92f2b8a4b4ff071062669525d5f866dbfa6752acfcef0f8eaac46092f6f.

The installed Android 37.2 Pixel 6 AVD had no assignment to this evaluation in the retained records.
No emulator was started or modified and no ADB mapping was made. Android journey, screen-level SMS
capture and performance samples remain unexecuted. T16/T38, independent review, and the separate
physical-handset profile and consent requirements remain open.

### Execution 39 dedicated emulator journeys

The Android and API production baseline remained upstream 5d99285077e192c3749a175e67e2b664b90e8e0b.
The starting evaluation checkpoint was 38d95ca3077f3a8e246255bd402a4cd2a5b4648c.
The dedicated FINORA_T55_Evaluation AVD used a Pixel 6 profile, Android 17 Google APIs 16 KB
x86_64 image android-37.2 (device API 37), WHPX, SwiftShader, four virtual cores, 4096 MB RAM,
1080 x 2400 at density 420, and a 6 GB data partition. It ran without a snapshot at emulator-5580,
with a private ADB server on 5039 and Android user/serial 0. No FINORA packages or reverse mappings
were present before installation. READ_SMS remained ungranted.

Installed APK SHA-256 read-back was 61566a20e6c82165d0dd4dfea85021f44d0dd48d39e0ca968736f51774eb62ed
for lk.finora.app.acceptance and 43a3b2d2c4292f4f0cbb23548dc8ad8a1a7f544cdca08b8d299773ea7ef0ffc9
for lk.finora.app.acceptance.test. Both used signer SHA-256
863fd92f2b8a4b4ff071062669525d5f866dbfa6752acfcef0f8eaac46092f6f, and the installed runner was
AndroidJUnitRunner targeting lk.finora.app.acceptance. The provider-disabled loopback API used the
retained finora_t55_perf schema with its existing Java HTTP(S) restriction. Process-level egress
isolation was not independently verified.

EvaluationJourneysTest passed one connected test with 18 visible-state assertions across both users.
The November Home values were LKR 60000.00/40000.00 and 25750.00/11000.00 (income/expenses).
Both users displayed a November Records row, a Shopping budget, their named bill and goal,
their expected contribution row, a numeric score and the rule-based guidance label.
Normal sign-out and replacement sign-in were exercised. The row presence and advice label checks
do not establish every financial value or advice item. Two earlier harness attempts failed,
first on the radio click return value and then on a retained session. Both harness issues were fixed.

A separate connected test passed paste-diagnostic cancel, preview and confirm on a newly registered
disposable user. It used an unrecognized synthetic input. Approved NSB import paste and inbox wire
capture, field allowlists and retry capture remain unverified. There were no qualified cold or warm
performance observations. Displayed-frame corroboration and HTTP/server timing correlation remain open.

A fresh read-back after the journeys preserved 826 transactions, all other canonical fixture counts,
both profile totals, and transaction digest 14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944.
The loader was not invoked. The session reverse mapping and private input file were removed.
The API, private ADB server and dedicated emulator stopped. The dedicated AVD and MySQL instance
remain available for the next session.

### Execution 40 synthetic SMS traffic

The dedicated emulator passed the approved three-format NSB screen challenge through paste and inbox,
with sanitized API evidence for confirmation, diagnosis and explicit retry. Benchmark data remained
unchanged. See [Execution 40 wire evidence](t53-android-sms-wire-evidence.md).
Performance and formal T55 acceptance remain open.

### Execution 41 emulator timing pilot

App and API production sources match upstream 5d99285077e192c3749a175e67e2b664b90e8e0b.
The evaluation started at 8cc1f922685f844728774aa9ec7543ca0c1a246c. The unchanged app
lk.finora.app.acceptance SHA-256 was 61566a20e6c82165d0dd4dfea85021f44d0dd48d39e0ca968736f51774eb62ed.
The measured pilot instrumentation APK SHA-256 was
14866289f7b1f51bb137f38f8f25770d198b465b38496601faa9bcdeab906087. The focused frame-token build was
849e4e2248f29aa13f2d74fc186d1143aff94f5f6325010682508273199b8155. Both used the acceptance runner
and signer SHA-256 863fd92f2b8a4b4ff071062669525d5f866dbfa6752acfcef0f8eaac46092f6f.

FINORA_T55_Evaluation ran Android 17/API 37 on Windows with Pixel 6 geometry, WHPX, SwiftShader,
four virtual cores and 4 GB RAM. The run used user/serial 0 on emulator-5580, private ADB port 5039,
and the D22 reverse to the provider-disabled 127.0.0.1:8080 API. The user and cache setup used
the regular synthetic profile, November 2026, English labels, and sequential Home, Records,
Plan and Insights cohorts. Warm trials retained the process, login and application/server caches.
The fresh-process Home cohort used am force-stop between separate instrumented invocations and
retained the encrypted session and disk/server caches. Its start was the instrumentation
activity-launch call, after the runner process had started. It excludes process initialization
before that boundary. No deep-screen process-cold journey was attempted.

Warm start was captured immediately before UI control dispatch. Readiness was the first pre-draw
where the required visible content passed. Home required both November owner totals. Records
required a November 30 transaction row. Plan measured the Budgets route and required Shopping
and its LKR 5000 limit. Bills and goals were outside that timed route. Insights required a visible
numeric score, visible pattern-period evidence or explicit empty-pattern text, and no visible
loading status. The frame-commit callback timestamp used elapsedRealtimeNanos and remains a
submission diagnostic.

| Route | Cohort | Attempts | Committed | Failed | Timed out | Median ms | Nearest-rank p95 ms | Max ms |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Home | Warm | 10 | 10 | 0 | 0 | 64.489 | 67.045 | 67.045 |
| Records | Warm | 10 | 10 | 0 | 0 | 253.683 | 361.509 | 361.509 |
| Plan / Budgets | Warm | 10 | 10 | 0 | 0 | 119.475 | 146.315 | 146.315 |
| Insights | Warm | 10 | 10 | 0 | 0 | 109.213 | 178.487 | 178.487 |
| Home | Fresh process, activity launch | 5 | 5 | 0 | 0 | 2122.222 | 2234.028 | 2234.028 |

Each warm route used five separate uncounted warm-ups. With five or ten measurements, p95 equals
the sample maximum. The first calibration run retained 15 Home rows and one Records warm-up
failure. Android replaced the observer as the Records window attached. The probe now reacquires
the live observer at pre-draw. The warm suite, five separate Home starts, and the four-route
trace-check suite each passed their connected instrumentation invocation after the fix.
[Raw Android rows](t55-performance/execution41-frames.csv).

The local Perfetto v59.1 processor parsed actual FrameTimeline and app action, ready, vsync, and
submission markers. Trace metadata reported clock ID 6 (BOOTTIME). For example, the Home action
slice was 363018703089 ns and elapsedRealtimeNanos was 363018676173 ns, approximately 27 us apart.
Display validation used only timestamps from the same Perfetto trace. The Home ready token 69545
matched the acceptance MainActivity surface for PID 7471 and SurfaceFlinger display token 69552.
The display slice ended at 363080701512 ns and reported On-time Present. Action to present was
61.998 ms in this separate trace-check observation. Records, Plan and Insights had no matching
app-surface tuple for their recorded ready token and remain unqualified. The original pilot trace
ring overwrote its early data and lacked exact ready-token instrumentation. No displayed-frame
cold estimate or route-level displayed p95 is claimed. [Local reproducible trace query](t55-performance/execution41-display.sql).

The server access log used the Tomcat connector's processing duration in microseconds and omitted
query strings, headers and bodies. Its observations include setup, warm-up, calibration and trial
requests. Per-endpoint n/median/p95/max in milliseconds: dashboard 39/27.764/49.628/79.272,
transactions 17/15.336/48.114/48.114, budgets 16/11.028/18.515/18.515, insights
16/14.426/131.324/131.324, score 16/17.313/131.996/131.996. Accounts, categories and me
also have independent rows. The app-client request duration was not instrumented. Server rows
cannot be paired to specific trials, and multi-request times were not summed.
[Raw server rows](t55-performance/execution41-server.csv).

Local pilot and qualification trace SHA-256 values are
4bb46e31bfbc9e28d7e26d6ff22e662f9a5edb98cff249548ca97594f9acdc1f and
35040faec9faf5ba8fbac42298a890e21ebd34eb2119febbe23e273eadff5476. The traces remain local in
the evaluation data parent directory. No trace was uploaded.

The two benchmark profiles retained all fixture counts and totals and digest
14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944. The loader was never invoked.
Temporary input was removed, the reverse mapping was removed, and the session API, ADB and AVD
processes stopped. The dedicated AVD and MySQL instance were retained. This pilot leaves T55.2
open for the approved handset, statistic, T16/T38 and independent review.

### Execution 42 displayed-frame qualification and startup investigation

The start commit was 5aed66fd48b5efb84abcafd389b82a62ea04664b. Upstream moved to
2916515c0651a986bf2868e06213b9b147a08ffb. D57 covers T17.3, T18.4 and T19.3 emulator
functional checks only. There is no Android or API production-source delta. The dedicated Pixel 6
AVD used Android 17/API 37, WHPX, SwiftShader, emulator-5580, private ADB 5039 and Android user/serial 0.
The regular profile and November 2026 were retained. The provider-disabled API was bound to loopback.

In the previous trace, Records preferred token 69737 differed from nearby rendered token 69734.
In this calibration Records preferred 9807 and rendered 9804, and Plan preferred 10660 and rendered
10658. The probe now listens on the destination Window, matches FrameMetrics.VSYNC_TIMESTAMP to
the Choreographer frame time at pre-draw, then records FRAME_TIMELINE_VSYNC_ID. The trace query
requires the same PID and destination layer, the exact surface and linked display token, and explicit
presentation statuses. Both app and display intervals use the same Perfetto BOOTTIME trace (clock ID 6).
A commit-callback arrival can occur after presentation and is kept as a separate diagnostic.

| Route / cohort | Attempts | Qualified | Unqualified | Failures | Timeouts | Median ms | p95 ms | Max ms |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Home, warm calibration | 1 | 1 | 0 | 0 | 0 | 81.839 | 81.839 | 81.839 |
| Records, warm calibration | 1 | 1 | 0 | 0 | 0 | 415.561 | 415.561 | 415.561 |
| Plan / Budgets, warm | 4 | 2 | 2 | 0 | 0 | 174.237 | 188.747 | 188.747 |
| Insights, warm calibration | 1 | 1 | 0 | 0 | 0 | 410.343 | 410.343 | 410.343 |
| Home, fresh process after runner startup | 3 | 3 | 0 | 0 | 0 | 2040.318 | 2072.454 | 2072.454 |

Two Plan frames had Unspecified Present on the display track. These targeted samples did not repeat
the full pilot. At these sample sizes nearest-rank p95 is the maximum. Home restart samples retained
the encrypted session and began at the instrumented activity launch after runner startup.
Earlier submission and current displayed measurements do not establish a speedup.

[Calibration](t55-performance/execution42-calibration.csv), [follow-up](t55-performance/execution42-followup.csv),
[submission rows](t55-performance/execution42-submissions.csv), [display query](t55-performance/execution42-display.sql).
Local traces execution42.perfetto-trace and execution42-followup.perfetto-trace have SHA-256
6bfa79ccee52f6e7f8bd4aaaf0e4f318ed5593954f186aa29d540d67110cea99 and
38e4f0aeb1feb139ea12ee91b124291a25404723865a3279e82acb314fb16528 in the evaluation data parent.
The app SHA-256 remained 61566a20e6c82165d0dd4dfea85021f44d0dd48d39e0ca968736f51774eb62ed.
Instrumentation SHA-256 was ddd1d3fb21cb60d9f0a00d4f76087bdf611391e713fb39fba9c25cd8cb4397f3
for calibration and 16ed4cc8c552d1e93f4bf975ca96da4a77ca3a9f7f6d94f4bc4ab73e7442e61f for follow-up.
Both used the acceptance runner and verified signer. Both builds and all five targeted instrumented
invocations passed.

#### Client and server timing

Five independent authenticated GET probes reused ApiClient and measured dispatch through body drain
on the device elapsed-realtime clock. Tomcat recorded server processing on its own clock. A generated
test-only X-Finora-Perf-Id joined the observations by ID, route and HTTP 200. No credentials or payload
values were retained. Client/server milliseconds were dashboard 40.913/34.139, transactions
20.214/15.203, budgets 10.535/8.111, insights 19.223/15.748 and score 20.488/17.775.
The screen requests did not carry test IDs, so individual screen attempts and their multiple
requests remain uncorrelated. [Joined rows](t55-performance/execution42-http.csv).

#### External launcher startup

One force-stop and external am start -W reported COLD, LoginActivity TotalTime 1221 ms.
Perfetto reported 1221.936 ms for the same cold launch. The first displayed LoginActivity surface
was at 1250.049 ms and the first displayed MainActivity surface at 2342.661 ms after trace startup.
The external launch had no financial-content oracle. Main-thread slices included bindApplication
353.771 ms, login performCreate 99.764 ms including a 57.116 ms inflate, first login traversal
531.634 ms, and MainActivity performCreate 138.761 ms and activityResume 212.632 ms.
These intervals can overlap. Server processing for the launcher sequence was refresh 20.515 ms,
me 16.984 ms and dashboard 40.236 ms. Individual app-side request boundaries, data transformation,
and cache effects were not isolated. No production optimization or equivalent before/after comparison was made.

#### Handset run preparation

1. Record device-owner consent, exact serial, isolated profile and Android user/serial, Android
   version and agreed mid-range classification. D57 leaves this gate open. Confirm the statistic
   and cohorts that govern the original under-two-second requirement.
2. Pin and verify the app/test APKs, signer, runner and profile. The current pilot runner permits
   the emulator only. Review an explicitly guarded handset runner and verify Android window
   metrics and FrameTimeline support on the selected operating system before execution.
3. Verify the retained database digest and provider-disabled loopback API. Check the baseline
   reverse map before issuing adb -s SERIAL reverse tcp:8080 tcp:8080 for the approved serial.
4. Run the November Home, Records, Budgets and Insights oracles. Add timed Bills and Goals
   only under an approved route decision. Keep the proposed five warm-ups and 30 measured warm
   attempts per route pending approval. Define a separate reproducible cold cohort and its launch
   boundary, plus failure, timeout and unqualified-frame handling, before running.
5. Retain all attempts and compute median, nearest-rank p95 and maximum on qualified displayed
   samples. Remove only session-owned inputs and mappings, and restore and verify baseline
   profile settings. T16, T38 and independent review remain formal gates.

The benchmark profiles retained all canonical fixture counts, totals and SHA-256. The loader was
never invoked. Temporary input and reverse mapping were removed. The API, private ADB server and
dedicated emulator stopped. The AVD and database were retained.

#### Publication replay boundary

Execution 43 carries the evaluation history onto the upstream T34/FCM revision. The APK and
connected evidence above remains tied to its original tested revisions. The publication revision
uses the same test-only emulator controls. Ordinary Spring tests exclude the capture configuration
and skip the explicitly enabled persistent load. The opt-in launchers pin the verified evaluation
database and blank provider and Firebase configuration. Rehearse handset instrumentation and
owner-approved routes against the combined source before collecting a handset acceptance sample.

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


## HMD Crest handset calibration (27 November 2026)

One guarded trace-check attempt per route completed on the owner-approved HMD Crest (Android 14, API 34) in a temporary secondary user. The previously verified APK pair was reused byte for byte. The isolated API used revision 5a1376ad859f244964f15dcfbf9bf87e3cdf53cb and executable SHA-256 084dbb1b7715b60247bffbd933dd24e6f5e437f0de1f3c8effa9c94d24578aa1. The retained MySQL session dataset matched all eight counts and the canonical 826-transaction digest before and after calibration. Normal login and the November worker dashboard oracle passed. The original benchmark instance on port 3317 was left untouched.

| Route | Action to submission (ms) | Action to qualified display (ms) |
|---|---:|---:|
| Home | 100.473 | Unqualified |
| Records | 714.614 | Unqualified |
| Plan | 325.515 | 343.091 |
| Insights | 277.700 | Unqualified |

The original query expected emulator marker names. The handset trace contained four vsync markers and used Android 14 surface names. The [handset query](t55-performance/handset-calibration-display.sql) matches process, activity surface, vsync token and display presentation. Plan had one matching BudgetListActivity surface and an on-time display event following content readiness and frame submission. Home, Records and Insights had no matching surface token. Trace SHA-256 d212a7395424c2390935ae0cb9025674e411cfe19c618184a8f85d1e3e5ee8aa. Raw trace and submission CSV are retained locally under session fdde4a9218ce.

The retry reused the verified database, API JAR, installed APKs and temporary user. Cleanup restored Owner as foreground, removed only the temporary user and session USB reverse mapping, stopped the session API and MySQL, and verified Sahan's FINORA-T10-QA user and package set. The earlier automated removal did not complete. Android package manager's wait-for-removal operation succeeded on the exact owned user.

This is mixed-revision calibration with one observation per route. T55.2 remains open for an approved device classification and measurement cohort/statistic, qualified samples for all required routes and independent review.


### Retained trace requalification

The API 34 harness emitted no rendered-token markers. Android documents its public FrameMetrics frame timeline ID as API 36. The three failed joins used the callback-preferred IDs 21028071, 21028845 and 21031333. The ready markers are nested in Choreographer traversals 21028068, 21028838 and 21031331. The same-process DrawFrames slices carry those traversal IDs, and each ID joins one destination activity surface and one actual display event. The prior trace and its original results remain retained.

| Route | Traversal token | Display end minus action |
|---|---:|---:|
| Home | 21028068 | 106.734 ms |
| Records | 21028838 | 727.575 ms |
| Plan | 21029976 | 343.091 ms |
| Insights | 21031331 | 296.690 ms |

The [corrected trace query](t55-performance/handset-traversal-display.sql) requires an exact process, traversal token, activity surface, unique display event and ordered timing markers. A no-match replay left all four rows unqualified. These results are a reanalysis of one historical observation per route.

### Targeted confirmation after approval

Use the original application APK and the separately signed instrumentation APK (SHA-256 8daf8e2aa6bd6bb75d0b6acb4bee6d3af4e17f9f17889871f7d57f31f54c9df0). The test-only overlay requires the literal route list HOME,RECORDS,INSIGHTS and filters the existing loop to those three destinations. The offline assembleV1DebugAndroidTest task passed. The package, AndroidJUnitRunner target and signer were checked.

The prepared local launcher refuses before ADB unless a connected session is explicitly approved. The approved run uses the retained session database and JAR, a new session-owned temporary user, and a single trace with one attempt per listed route. Record all three rows and retain the trace even if a presentation cannot be qualified. Preserve the baseline users and package identities, remove only the session USB reverse and profile, and recheck the dataset digest. No second connected session has run. T55.2 acceptance remains open.


### Targeted handset confirmation (27 November 2026)

The approved HMD Crest API 34 session ran Home, Records and Insights once each. The launcher reused the verified application APK, API JAR and session-owned synthetic data. The targeted instrumentation APK passed signature, package and runner checks. All three attempts completed and qualified against the destination surface and actual display timeline.

| Route | Action to submission | Action to qualified display |
|---|---:|---:|
| Home | 74.335 ms | 86.954 ms |
| Records | 271.045 ms | 297.397 ms |
| Insights | 232.301 ms | 253.385 ms |

[Sanitized timing and token rows](t55-performance/handset-targeted-display.csv). Perfetto SHA-256 e223ca44c2a65215fd7c3600c5bd7db82de74a99ab8c3f506f08daffe3aa1df5. The raw trace remains in the local evidence session. The historical Plan qualification remains 343.091 ms.

The launcher stopped the API and MySQL and removed its USB reverse mapping. Automated profile cleanup needed a follow-up. Android acknowledged removal of the exact temporary user. A read-back confirmed Owner was foreground, the temporary profile was absent, both Sahan APK paths matched, and no reverse mappings or session listeners remained. The dataset digest and eight counts matched before and after the session.

These are calibration observations. T55.2 remains in progress pending the approved device classification, routes, cohort, statistic and independent acceptance.

## D65–D71 provisional HMD Crest measurements (28 November 2026)

One temporary profile ran the V1 debug acceptance build from main `b2262bfaca2d94ed200bce122682d0ddc4c054e9`. HMD Crest, board Arrow, Android 14 (API 34), 1080 × 2400, and 5,543,588 kB reported RAM. The project has no recorded mid-range classification approval for this handset. The temporary profile used English and USB reverse to the provider-disabled loopback API. The retained API executable from `7c706f7534cecd5ef7abd6aaf2ff9acffdf54b37` is source-equivalent for the API. Its SHA-256 is `9a586e1d0f21f96205cef9d57886258f625ebdeb912183caa9adb1633089dfa2`.

App APK SHA-256 `caf79c69163dbb87ada05853a6106eecd455615ebd5ee305431b49d6bdc32881`. Measured instrumentation APK SHA-256 `b0fd2ae3a8b145fd01e0b60ada9ebc94aa9267ce2e40408a6a03d10e2caf7d08`. Both APKs used the matching debug certificate and their installed bytes matched the candidate files. The API migrated only a disposable copy to V25. The two canonical owners' eight counts and 826-transaction SHA-256 `14e563da86de67d4f295b6a9f8577d76ec781128b9c91900e6a1b54c96069944` matched before and after. The frozen data directory was never started or migrated.

Measured durations use a monotonic action marker, the first traversal meeting the screen's content oracle, and a unique FrameTimeline match to an actual presentation. The reported percentile is nearest rank. Warm-up rows are excluded. [Sanitized observation rows and trace hashes](t55-performance/handset-redesign-observations.csv)

| Route / cohort | Planned | Qualified | Unqualified | Unrecorded | Failed or not run | Median ms | p95 ms | Max ms |
| --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| Records warm | 30 | 30 | 0 | 0 | 0 | 87.597 | 150.135 | 163.762 |
| Home warm | 30 | 0 | 26 | 4 | 0 observed | — | — | — |
| Insights warm | 30 | 0 | 27 | 3 | 0 observed | — | — | — |
| Plan to Budgets warm | 30 | 0 | 0 | 0 | 30 not run | — | — | — |
| Home fresh-process activity launch | 5 | 5 | 0 | 0 | 0 | 1,167.915 | 1,210.928 | 1,210.928 |

All five Records warm-ups qualified. Home warm-up rows were unrecorded. Insights retained two unqualified warm-up traversals and three unrecorded warm-up rows. The first Budgets warm-up failed before action dispatch because both a heading and a button used the same label. The remaining four warm-ups and all warm attempts for Budgets were not run. The retained Home and Insights warm traces lacked enough presentation coverage. Unrecorded rows retain no timing or success claim. No explicit timeout was retained.

The cold samples forced a process stop before each instrumentation launch. The reported boundary begins at the instrumented activity-launch action and ends at the displayed usable Home. The traces have no consistent process-start timestamp, so these rows supply no full process-start-to-display statistic. With five attempts, nearest-rank p95 equals the maximum.

The follow-up test matches the clickable navigation button and streams each timing status directly through instrumentation. The host runner waits for the foreground Perfetto capture to finish before retrieving it. The updated instrumentation built offline and the SQL replayed on retained traces. The post-run fixes still need handset requalification under a separately authorized session. The formal device classification, acceptance statistic, routes and cohort decision, T16/T38 closure, and independent review remain open. No two-second acceptance verdict was assigned.

Android returned to Owner. The temporary user and candidate packages were removed, both Sahan QA packages remained, and the USB reverse, API, MySQL and session-started ADB listeners were removed or stopped. The isolated database copy and traces were retained locally as evidence. No provider traffic or participant data collection occurred.
### Targeted warm follow-up and retained cold replay (28 November 2026)

This additional HMD Crest session used the same application APK from the earlier D65–D71 run, SHA-256 `caf79c69163dbb87ada05853a6106eecd455615ebd5ee305431b49d6bdc32881`. The corrected instrumentation APK SHA-256 was `e8854ef5ffa9d36b992f423c74918a039ea13b563695eb9bed7e0fdd5e5c4b78`. Both installed bytes and the matching debug signer were verified. The PR #43 base added tracker changes only. Records retains 30 qualified warm observations from the same source and dataset. No additional Records or cold launch was run.

| Warm route | Capture rehearsal | Full warm cohort |
| --- | --- | --- |
| Home | 3/3 displayed frames recovered at 138.375, 179.770 and 211.580 ms, listed in ascending order | Not started |
| Insights | 3/3 displayed frames recovered at 211.349, 214.790 and 267.398 ms, listed in ascending order | Not started |
| Plan to Budgets | ADB transport failed before instrumentation. No rehearsal action was recorded. | Not started |

The automated gate stopped on the handset's unsupported `ps -o ARGS` field at trace finalization. The exact session-owned traces were recovered, replayed, and removed from the handset after capture. The [sanitized calibration rows](t55-performance/handset-targeted-warm-calibration.csv) include the Budget transport failure and unstarted slots. Rehearsals remain outside measured warm cohorts.

The launcher now uses `PID,CMDLINE` to verify the exact Perfetto command. It preflights this field before any profile change, retains the ADB exit status, and reports incomplete cohorts as a failure. The device accepted the corrected field and the exact trace-owner match during recovery. The amended launcher still needs a connected end-to-end run for the three missing warm cohorts.

The retained cold trace supplies five unique Android `system_server` `Start proc` operations, each after the previous Home presentation and before the next measured launch action. The [SQL](t55-performance/handset-cold-process-request.sql) joins each to its qualified displayed Home frame and [retains all five timings](t55-performance/handset-cold-process-request.csv). Process-start request to displayed Home had median **2,151.854 ms**, nearest-rank p95 **2,251.399 ms**, and maximum **2,251.399 ms**. This interval includes instrumentation startup. It does not establish kernel fork time or launcher-tap latency. The earlier activity-action statistic remains separately valid. No formal two-second verdict was assigned.

The disposable V25 copy matched all eight canonical-owner counts and the 826-transaction fingerprint before and after the session. Read-back confirmed the temporary profile and candidate packages were absent, Owner and Sahan's two QA packages were preserved, and no session Perfetto, USB reverse, API, MySQL or ADB processes or mappings remained. The frozen database was untouched.

## HMD Crest trace finalizer recovery, 28 November 2026

The one approved temporary-profile session used PR #43 head
`2a9e761e44d7e709967f900a90852f5d93015127`. Upstream
`0e175d8a776b69ad6b656f8e387640edcab65ebf` changed only decisions and
trackers since the previously inspected baseline. The qualified Records
cohort is retained (30/30, median 87.597 ms, p95 150.135 ms, max 163.762 ms).

Home's three rehearsal attempts committed and all three qualified on replay of
the recovered Perfetto trace (168.890, 180.206 and 181.542 ms). The
[raw timing rows](t55-performance/handset-home-finalizer-recovery.csv) use the
ready marker's traversal and the corresponding SurfaceFlinger presentations.
The callback timestamp can be later than presentation, so it is corroborating
diagnostic evidence and is excluded from the duration.

The direct ADB trace-finalization preflight succeeded. The full launcher
failed during Home calibration finalization with `ps: bad CMDLINE` because
PowerShell constructs an array from the unquoted field list at the wrapper
boundary. Passing the quoted field through that same wrapper identified the
exact trace PID, allowed an interrupt and flush, and recovered the trace.
The runner and preflight now both pass a single quoted argument. No warm
measured cohort (Home, Insights or Budgets) started. No measured attempts
were discarded. The launcher correction was verified at the argument and
trace-recovery boundaries, with a complete connected rerun still pending.

The prepared external cold route force-stops the app and starts the real launcher
activity through Android's activity manager, without an instrumentation runner.
Its query reports system-server process-start request to first MainActivity
presentation, which may contain placeholder content. A replay on the retained
instrumentation-mediated trace exercised the query and returned three observed
presentations and two unmatched starts. That replay does not establish independent
cold startup. The new external route did not run after the warm capture fault,
and there is no newly confirmed production performance defect.

The disposable database matched the frozen 826-transaction digest before and
after the session. Cleanup returned to Owner, removed the temporary user,
preserved FINORA-T10-QA and its installed packages, restored the reverse
mapping baseline, and removed the recovered remote trace. The session-owned
ADB, API and database listeners were gone. T55.2 remains open, including the
formal device classification and measurement acceptance decision.

## Launcher lifecycle qualification on the isolated emulator, 28 November 2026

The build with `-PfinoraFinalHandset=true -PfinoraT55Capture=true` assembled
the V1 debug app and its Android test APK using Android Studio JBR. The marker
is limited to the opt-in debug package and observes the two expected Home
balances in a natural pre-draw. It leaves the normal app and release code
paths unchanged. The evaluation API reused the verified V25-compatible JAR.

The dedicated `FINORA_T55_Evaluation` AVD ran read-only on Android API 37
(1080×2400, approximately 4 GB RAM) with private ADB. The launcher injected
a failure while the foreground capture was active. The guarded finalizer
found the capture by its quoted `PID,CMDLINE` field list, interrupted the
exact process, waited for it to flush, pulled the trace and verified remote
removal. Subsequent routes ran. An earlier trial confirmed that insufficient
Home and Budgets presentation matches blocked their corresponding full
cohorts while independent routes continued.

The final preflight exited successfully. Insights cleared the 3/3 rehearsal
gate. The full capture retained all five warm-ups and 30 measured attempts
without a reported trace loss. Of the measured attempts, 27 qualified and
three remained unqualified. Of the warm-ups, three qualified and two remained
unqualified. [Emulator warm rows](t55-performance/emulator-lifecycle-insights.csv).

All five external launches followed `am force-stop` and Android confirmed
`LaunchState: COLD`. The query uses the system-server process-start request
as its start boundary. Five first app presentations and three usable Home
presentations qualified. The other two usable outcomes remain unqualified.
The first-app boundary can include empty or loading content. The usable
boundary requires both synthetic financial balances in the traversal that
matches a presented Home surface. The external launches used no
instrumentation runner, though tracing and the debug-only marker were active.
[Emulator cold rows](t55-performance/emulator-lifecycle-cold.csv).
Cold trace SHA-256: `a69149ada4492c6725eb6a392f9fb6480516de995c28a2c5b4055be4e8661489`.

The 826-transaction digest and eight fixture counts matched before and after.
The emulator temporary user and reverse were removed, the original emulator
user was restored, and the launcher, private ADB, API and disposable MySQL
listeners stopped. This preflight was emulator-only. The previous
30/30 Records handset cohort remains source-equivalent. Formal T55.2
acceptance remains open.

## Targeted HMD Crest warm cohorts and independent cold launch, 28 November 2026

One isolated HMD Crest session followed the successful emulator lifecycle
preflight. The handset reported Android 14/API 34, build
`00WW_1_59I`, 1080×2400 and 5,543,588 kB total RAM. Four model-verified
ADB round trips preceded profile creation. The tested harness was
`2418cbf4a837451f40ed35d6e425ebd2f20e7287`. The provider-disabled
V25 API reused the verified JAR and disposable synthetic fixture.

| Timing in ms | Attempts | Qualified | Failures / timeouts / unqualified | Median | Nearest-rank p95 | Max |
| --- | ---: | ---: | --- | ---: | ---: | ---: |
| Home warm | 30 | 30 | 0 / 0 / 0 | 123.616 | 143.380 | 192.390 |
| Records warm, retained | 30 | 30 | 0 / 0 / 0 | 87.597 | 150.135 | 163.762 |
| Insights warm | 30 | 30 | 0 / 0 / 0 | 201.519 | 325.799 | 349.957 |
| Budgets warm | 30 | 30 | 0 / 0 / 0 | 168.346 | 189.719 | 192.134 |
| Cold: first app presentation | 5 | 5 | 0 / 0 / 0 | 773.879 | 851.188 | 851.188 |
| Cold: usable Home presentation | 5 | 5 | 0 / 0 / 0 | 1633.954 | 1730.653 | 1730.653 |

Every new warm route qualified 3/3 rehearsal frames and 5/5 warm-ups before
or in its full capture. The [114 raw warm rows](t55-performance/handset-targeted-final-warm.csv)
include those uncounted phases and all 90 measured observations. Perfetto
reported no data loss. The prior source-equivalent Records cohort remains
linked in its original section. The budget screen is labeled `PLAN` in
the raw route field.

The five [raw cold rows](t55-performance/handset-targeted-final-cold.csv)
came from force-stops and external launcher activity starts. Android reported
`COLD` for all five. Both boundaries start at the system-server process-start
request. First app presentation can contain loading content. Usable Home
requires the same presented frame as the natural pre-draw with LKR 60,000.00
income and LKR 40,000.00 expenses. There was no instrumentation runner on
the cold start path. The debug readiness observer and tracing were active.
The historical runner-mediated cold observations are a separate method. Five
cold samples provide limited percentile resolution.

Verified debug APK SHA-256 values:
- App `06a08b5910f8b02b50a08917f665cbdca0b23704f6f61650d604bcee37e4a820`
- Android test `0f2155780351e5f483c13ba2eeB8d27acc69ed2d0a3ab97c70663931a5cad692`
- Signer `863fd92f2b8a4b4ff071062669525d5f866dbfa6752acfcef0f8eaac46092f6f`

The disposable V25 copy matched the benchmark digest and all eight counts
before and after. The frozen database was untouched. Final read-back
verified temporary user removal after the Android user-stop transition,
restored Owner and Sahan's original QA profile and package paths, and
confirmed the reverse mapping baseline. Session traces were removed from
the phone. No session Perfetto, API, MySQL or ADB listener remained.

These measurements complete the requested physical cohorts. Formal T55.2
acceptance still requires the documented device classification and
measurement criteria and independent review.
