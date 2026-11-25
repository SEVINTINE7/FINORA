# T55 evaluation preparation — no measured results yet

2026-11-22. Owner: Thilina; execution with Sahan after T16/T38. This is a reproducible protocol/template,
not evidence that SMS accuracy, latency or usability targets have been achieved. No Android changes made.

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
procedure/commit. Run against isolated finora_test. Record mid-range phone model/OS, build type, API host/hardware,
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
| SMS field accuracy >=90% | Not collected; T14/T16 fixtures/parser required | Blocked |
| Main screens <2 seconds | Not measured; T38 and real phone required | Blocked |
| SUS >=70 | No participants/responses collected | Blocked |
| Advice comparison | D03/protocol and live-call approvals unresolved | Blocked |

For each future run attach run ID, commit/device details, denominator/raw result reference, observed result,
reviewer, limitations and defect/next action. Keep T55 incomplete until real evidence and review exist.
