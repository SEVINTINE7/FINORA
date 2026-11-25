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
