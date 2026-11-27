# PR #30 focused correction evidence

Reviewed publication: `d796de004dfcfdc52149a7620ae9f8f40be1db19`, based on
`bf2af3d08d51a0dda901775833800c4e1c5ce445`. The published checkout and GitHub's
prospective merge had the same tree, `a17506cf251a38fa5ee99aab3e376c3788b9d5d5`.
GitHub reported both jobs successful in
[run 45788155948](https://github.com/SEVINTINE7/FINORA/actions/runs/45788155948).
That hosted result applies to the published revision, not the follow-up below.

## Corrections

1. **T29 numeric signs.** The published separator class included Unicode space
   separators and format characters, but omitted line separator U+2028 and
   paragraph separator U+2029. Either character between a minus sign and an
   amount could let the verifier read the amount as positive. The correction
   uses all Unicode separator categories while retaining the existing dash,
   format-character and positive-text behavior. A later invalid section still
   rejects the complete reply, returns canonical advice and makes no backup call.
2. **T31/T32 rotation.** Restored category-cut controls now receive the busy state
   after they are created. They cannot be edited while the request for the
   previous draft is still in flight.
3. **T32 retry.** Editing baseline or adjustment after initial goal/category
   loading fails preserves that loading error and its Retry action. Ordinary
   calculation errors and stale results still clear when their inputs change.
4. **T55 launcher configuration.** Direct Spring datasource/Flyway overrides,
   alternate pool settings, profile additions and Maven option/base-directory
   overrides are removed from the child execution environment. Both connection
   settings are pinned to the newly verified disposable database. Normal
   configuration naming and the appropriate test/dev profile are pinned;
   provider/Firebase settings are disabled. The calling process's prior
   environment is restored. Passwords stay out of command-line arguments.
5. **T55 launcher cleanup.** Permission restoration and reverse-mapping removal
   are independent best-effort actions. Their failure cannot skip the attempt
   to terminate the session-owned emulator. A failed console shutdown falls back
   to the recorded process handle.

## Executed verification

- **Backend failure reproduced:** the expanded `D58ReviewTest` ran on the
  published production source with Java 17: 9 invocations, 3 failures, 0 errors,
  0 skips. Both new later-section cases incorrectly returned accepted AI advice.
- **Backend correction:** offline Maven `test` passed 33/33 with zero failures,
  errors or skips: `AdviceVerifierTest` (20), `D58ReviewTest` (9),
  `DailyGuideServiceTest` (2), and `DailyGuideAggregateConfirmationTest` (2).
  Mockito was preloaded as a startup agent in this Linux environment. The
  later-section regression uses a synthetic LKR 75,000 income and LKR 17,500
  monthly goal-transfer case. These were unit tests with mocked provider clients;
  no database or external AI request was used.
- **PowerShell parsing:** the corrected launcher parsed successfully with
  PowerShell 7.7.0 on Linux. This does not establish Windows execution.
- **Launcher components:** `scripts/Test-FinoraLocalEvaluation.ps1` passed
  10/10 checks on the corrected script. Against the retained original PR #30
  script it passed 3 and failed 7, detecting the expected configuration and
  cleanup faults. The harness executes selected actual launcher statements in
  a child process with synthetic settings; ADB and process operations are
  mocked. It checks restoration and normal, permission-failure,
  mapping-failure, combined-failure, shutdown-failure and identity-mismatch
  cleanup. It does not start the API, MySQL or an emulator.
- **Source checks:** `git diff --check` passed. The two Android changes were
  reviewed against the actual control flow and the sole invalidation call site.

The full Maven gate, Android compilation/lint and connected journeys have not
been rerun on this follow-up in the Linux review environment. The original
publication's full-suite and emulator evidence remains tied to its original head.

## Required laptop checks for this follow-up

Run the affected Android build/JVM/lint checks and two controlled connected
regressions on the dedicated FINORA AVD:

- Hold a simulation response, rotate with a category-cut row present, and check
  that the recreated category/amount controls remain disabled until completion.
- Fail initial goal or category loading, edit a numeric draft field, then verify
  that Retry remains available and successfully reloads the choices.

Exercise the corrected launcher in its normal Windows environment with a fresh
disposable database. Configuration and cleanup component checks are separate
from that end-to-end execution. Preserve the frozen benchmark and the original
Execution 69 checkpoint. Production D58 semantic-policy adoption, deployed HTTPS,
physical-device acceptance and live-provider evaluation are outside this fix.


## Windows follow-up

The component checker passed 10/10 in PowerShell 7 on Windows. Verify and Connected
each started a fresh, separate loopback database with deliberately incorrect inherited
Spring endpoints. Verify also received an incorrect Maven JVM override. The launcher
used the disposable instances and restored the caller's settings. Java 17 verification
reported 505 tests, zero failures, zero errors and one intentional persistent-loader
skip, with V1–V24 migrations, JaCoCo and packaging passing. Ordinary V1 debug assembly,
152 JVM tests, full JBR lint and both acceptance APK builds passed.

Four connected journeys passed on the dedicated API 37 emulator. A test interceptor
held a real successful simulation response while the screen rotated in landscape.
The restored category, amount, remove, goal, baseline and adjustment controls stayed
disabled until release, then the screen reported LKR 21,500.00 per month. The other
regression returned one synthetic category-list failure, edited the numeric draft,
then explicitly retried and loaded the actual choices without losing the edit. The
existing planning and synthetic SMS import/Sinhala journeys also passed. The launcher
completed session cleanup. The frozen benchmark remained outside both databases.
