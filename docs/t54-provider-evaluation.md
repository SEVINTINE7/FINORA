# T54 Provider Evaluation

Date: 2026-11-25

## Verified baseline

- Integrated backend: 441/441 PASS.
- Coverage gate: PASS.
- Baseline: da0236fabe8ad5f4ef5b4802d7208932fa2d462e.
- Harness: T54ProviderEvaluationIT.
- Harness SHA-256: 78096E12FBF59DE9EE1E413F36DBF9732732FFBFC376AB5CDFA5CABCE2AE9424.

## Execution 22: dry-run results

- Financial fixture oracles: 6/6 PASS.
- Simulated exact candidates: 10/10 accepted.
- Numerical mismatches: 10/10 rejected.
- Simulated transport failures: 10/10 handled.
- Canonical rule fallback: 20/20 PASS.
- No-records controls: 2/2 PASS.
- Actual external HTTP attempts: 0.
- Explicit JUnit dry-run method: 1/1 PASS.
- Maven exit: 0.
- Failures, errors and skips: 0.

## Evaluation boundaries

The 30 simulated scenarios are not 30 JUnit tests.
Simulated provider results are not live accuracy measurements.

The actual advice service pipeline, financial facts,
strict verifier and rule fallback were exercised.
No deployed Android or billing acceptance is claimed.

Proposed live evaluation:

- Five eligible financial fixtures per provider.
- One zero-call control per provider.
- Maximum ten live candidates.
- Maximum twenty application HTTP attempts including retries.
- Zero expenditure and synthetic data only.
- Separate recorded authorization required.
- Exact account-specific model and free-quota checks required.
- No automatic rerun.

T54 acceptance remains open pending live evidence,
applicable dependency approval and independent review.

## Execution 24: live evaluation

Date: 2026-11-25.
Frozen baseline: e5f8bbd39433646de3ffd4531e5ef3a99e996bab.

Owner conditional authorization confirmed.
Free quotas and team authorization user-confirmed.
Billing reconciliation remains pending.

Execution used a detached evaluation checkout with
single-attempt provider transport and global stop.

- Compilation: PASS.
- Database identity: PASS.
- Financial oracles: 6/6 PASS.
- No-records controls: 2/2 PASS.
- Eligible live candidates: 10.
- Attempted candidates: 1.
- Accepted candidates: 0.
- Correct actual rule fallbacks: 1/1.
- Unexecuted candidates: 9.
- Application HTTP attempts: 1.
- Automatic retries: 0.
- Global stop: PASS.
- Frozen checkpoint: PASS.
- Live Maven exit: 1.

Gemini model: gemini-3.8-flash.
Fixture: worker.
HTTP response: 503.
Transport latency: 2337 ms.
Pipeline evaluation latency: 2355 ms.
Verifier outcome: PROVIDER_FAILURE.
Schema, claim and numerical verification: NOT_RUN.
Canonical rule fallback: PASS.

Groq model: openai/gpt-oss-120b.
All five Groq candidates remained unexecuted.

Numerical accuracy is unavailable, not zero percent.
Actual provider expenditure is not yet verified.
Application sends are not provider billing counts.

Isolated adapter SHA256:
3CF952C019F55543D318348D3D2E2DED87BEFF8C8CFB917C910C1617B6ADE16A

Isolated harness SHA256:
3D76FED9F12255037DDEC6E66A2DCEBC04924BD944D08A3D8158C7AF9721CFA9

Sanitized results SHA256:
09C18F2642EC59538115103F3E8AC2D5A3CB3B86363C571D4B5145F714DFC5FC

Strict-stop patch SHA256:
92991007751F3CF854BBE8D0CF702BE4F2043BE1992134EEF9B8C181FDE65A21

Classification:
LIVE_EVALUATION_BLOCKED.
REAL_FALLBACK_VERIFIED.
STRICT_STOP_PASS.

The original production retry policy remains unchanged.
No deployed Android acceptance is claimed.
No automatic rerun or provider substitution authorized.
T54 remains open pending genuine live acceptance,
billing reconciliation and independent review.

## Next bounded live run (prepared, not authorized)

Execution 24's detached checkout remains at e5f8bbd39433646de3ffd4531e5ef3a99e996bab.
The strict-stop adapter and harness files still match the recorded SHA-256 values above.
The merged harness permits provider retries and provider-specific stopping. Use only the
isolated single-send, global-stop profile for this plan. Before any later live run, verify
the exact source and toolchain, reconcile the isolated patch with the selected revision,
and run an offline fake-transport preflight of a first-send failure. Require one send,
nine skipped candidates, two zero-call controls, and no backup invocation. Keep the
production retry policy unchanged.

### Release gates and invocation

1. Record authorization for the exact account, model pair, quota, deadline and maximum
   sends. Reconcile Execution 24's single Gemini 503 against provider usage and billing.
   Confirm remaining free quota and no automatic paid fallback for both accounts.
2. Use the authorized loopback finora_test instance and Java 17 in the isolated checkout.
   Verify database name, host, port and instance identity before starting Maven. Run
   T54ProviderEvaluationIT#dryEvaluation with absent provider keys and require 6/6 oracles,
   10/10 simulated acceptances, 20/20 fallbacks and 2/2 zero-call controls.
3. After authorization, inject provider secrets out of band. The historical candidate
   identifiers are gemini-3.8-flash and openai/gpt-oss-120b. Reconfirm both on the approved
   accounts; a mismatch stops the run. Set FINORA_T54_APPROVED=true and
   FINORA_T54_HTTP_LIMIT=10 only for that process. Keep production advice providers disabled
   and preserve the strict harness model checks.
4. Select only T54ProviderEvaluationIT#liveEvaluation in Maven Surefire. Pass
   -Djdk.httpclient.disableRetryConnect=true and -Djdk.httpclient.enableAllMethodRetry=false.
   The verified isolated profile must prohibit redirects, retries and automatic failover.
   Never run the live method from the ordinary merged checkout.

### Fixed order and stop rules

- Before any outbound request, run the no-records control for both providers.
- Run Gemini first, then Groq. Each uses worker, small-saver, unemployed, break-even and
  cent-rounding in that order. Use November 2026 values and exact LKR oracles in
  [t54-financial-fixtures.md](t54-financial-fixtures.md). Maximum ten candidate sends
  overall, one per fixture/provider. Controls consume no sends.
- Stop globally on invalid setup, any failed preflight oracle, budget exhaustion,
  any terminal adapter failure (including 429, 503, timeout and unusable envelope), or an
  unverified first Gemini worker response. Stop if trace counters or expected fallback
  differ. Mark all remaining candidates skipped. A later verifier rejection with a usable
  envelope counts as a rejection, and the canonical fallback must match exactly. No
  automatic rerun or alternate model.

### Evidence and denominators

For each assigned candidate, retain provider, model, fixture, attempted/skipped disposition,
HTTP status if available, application-send count, adapter and pipeline durations, token counts
if reported, schema/claim/numeric verdicts, rejection class, and exact canonical fallback verdict.
Use sanitized metadata. Keep absent provider usage fields unavailable.

Report the ten scheduled candidates, attempted, accepted, verifier-rejected,
transport or envelope failed, and skipped counts separately. Accepted, verifier-rejected,
transport or envelope failed, and skipped must sum to ten. Report schema and
claim acceptance over replies with an assessable envelope, numeric pass and checked counts
for assessable facts, and canonical fallback over cases that required it. A response that
never reached numeric verification contributes no numeric checks. Report the two no-records
controls separately. Keep provider transport latency, pipeline duration, and usage observations
separate. Reconcile actual provider requests, token counts and expenditure after execution
before interpreting quota or billing. Execution 24 fallback evidence is not a live AI accuracy
measurement.
