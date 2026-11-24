# MD05 implementation checkpoint

Baseline `b70c36a`. D45 and D46 record scoped delegation and early-start authority. Original ownership, formal dependencies and independent review still apply.

| Requirement | Implementation and remaining evidence |
| --- | --- |
| T40.1 | Existing encrypted token storage retained. Release artifact checks cover backup rules, cleartext and recognizable credential signatures. Runtime release logging evidence remains open. |
| T40.2 | Release backup disabled and token exclusions retained. Activity windows and app-owned dialogs use secure windows. Retain platform-specific screenshot and Recents checks in the device checklist. |
| T40.3 | Android 17 verified process restart, rotation, offline retry and expired refresh rejection. A replacement login could not see the prior login's account. |
| T40 Done when | Record device checklist results and sanitized Logcat evidence for tokens and SMS. |
| T48.1 | Pending approval of saved-reply ownership, lifecycle, validation and starter text. |
| T48.2 | FAQ warns against sharing sensitive information. Saved-reply privacy enforcement remains open. |
| T48.3 | Authenticated USER FAQ available under More → Help, with English and Sinhala content. |
| T48 Done when | STAFF-only template management and privacy tests remain open. |
| T49.1 | Pending approved diagnostic contract for unknown bank and format metadata, and allowed error codes. |
| T49.2 | Pending defined grouping and ADMIN escalation behavior. |
| T49.3 | Pending ADMIN-only format mutation contract and STAFF denial test. Existing USER read-only format endpoint remains unchanged. |
| T49 Done when | Pending payload allowlist verification and format-mutation authorization tests. |
| T51.1 | Espresso journeys cover registration, login, account setup, transaction create/edit/delete, budget, dashboard and the SMS-declined path. |
| T51.2 | Requires Android 8 and a recent real device, or an explicitly approved alternative. Android 17 emulator evidence is separate. |
| T51.3 | Preserve sanitized reports and approved device evidence. Screenshots of secure screens should demonstrate capture blocking. |
| T51 Done when | Requires attached connected test output and completion of the acceptance matrix. |

| Task | Original owner | Formal prerequisites |
| --- | --- | --- |
| T40 | Sahan | T37, T08 |
| T48 | Sahan | T46, T47 |
| T49 | Sahan, Sevindu supports backend | T46, T45 |
| T51 | Sahan, Thilina supports QA | T38 |

## Delegated work

- **Sevindu, T46.1–T46.2.** STAFF-only queue stats and Android home use the existing tickets table. Counts include OPEN and IN_PROGRESS only. The endpoint returns counts without ticket bodies or customer data. Nonauthenticated, USER, ADMIN and disabled STAFF requests are denied by the existing authorization flow. No migration or deployment configuration change is needed.
- **Sevindu, T41/T43/T45/T47.** Confirm the exact staff provisioning and disablement workflow, permitted ticket content, redaction and transitions, and ADMIN format creation, update, activation and disablement rules. Confirm the escalation recipient, payload, lifecycle and access rules. Approval should preserve the existing T45.3 USER read contract and server disablement semantics.
- **Sahan, T48/T49.** Confirm who can create, edit and retire saved replies, whether templates are global or per staff member, approved starter copy and sensitive-input handling. Confirm a finite diagnostic enum and how unavailable bank or format metadata is represented. The proposed diagnostic payload is restricted to bank code, format version and error code, with no raw SMS or financial identifiers.
- **Thilina, T51/T52.** Confirm the exact approved recent-handset invocation or explicitly approve an alternative. Retain independent role-matrix review. The connected journey is guarded to run only on an opted-in isolated emulator. The original session harness remains available via the `-PfinoraSessionVerification=true` build option.

## Verification and handoff

- Run `connectedV1DebugAndroidTest` only with the intended isolated emulator visible to ADB, a disposable backend mapped to the debug loopback origin, READ_SMS revoked, and `-Pandroid.testInstrumentationRunnerArguments.finoraIsolatedEmulator=true`. No personal profiles or sessions.
- Group the future device privacy, resilience and bilingual accessibility checks under the consolidated testing guide. Record the original settings and restore them after the session. Keep automated and audible human evidence separate.
- The new Sinhala strings are listed in the MD05 review inventory and await explicit approval. Prior reviewed values are preserved.

### Retained command output

```text
[INFO] Tests run: 324, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
Starting 2 tests on FINORA_T11(AVD) - 17
Finished 2 tests on FINORA_T11(AVD) - 17
> Task :app:lintV1Debug
BUILD SUCCESSFUL in 1m 11s
```

The connected XML reports 2 tests, 0 failures, 0 errors and 0 skips. Android JVM XML reports 140 tests, 0 failures, 0 errors and 0 skips. V1 debug and unsigned release assembled, and lint reported no issues. MySQL applied V1–V13 on the dedicated verification schema, and all 27 D39 concurrency cases passed. The release scan checked 656 app-owned classes for calls to Android logging, standard output and HTTP logging. It also checked for common key signatures and key files.

On Android 17, separate live checks verified the Sinhala USER FAQ and STAFF login, two queue counters, screenshot suppression and sign-out. The opt-in session runner APK built successfully and its packaged manifest names the original runner. The default runner uses Espresso 3.7.0 to avoid obsolete InputManager reflection. An emulator journey also exposed the FAQ's system-bar inset issue, which was corrected and retested. Recent-device Recents, lifecycle/resilience, runtime redaction and consolidated accessibility evidence remain open.

Tracked acceptance is T40 2/4, T48 1/4, T49 0/4 and T51 1/4, for MD05 4/16. Delegated T41 0/4, T43 0/4, T45 1/6 (retained T45.3), T46 2/3 and T47 0/5 are counted separately. T52 remains in progress with no new checkbox.

Android 8/API 26 was unavailable. The official SDK command-line tools download attempt timed out: `curl: (28) Connection timed out after 10006 milliseconds`. The connected run used Android 17 only. Physical instrumentation was not run.

Pending approval details, proposed bilingual saved replies and the consolidated handset checklist are in [MD05 owner decisions and handset checklist](md05-owner-proposal.md).

## Android 17 follow-up on `84daafa`

- Isolated Android 17 with a MySQL-backed disposable API restored a USER session across force-stop and relaunch. The dashboard survived a genuine portrait-to-landscape change. The original rotation settings were restored and read back.
- Removing only the test reverse route reproduced offline session restore failure. Restoring the route and pressing the explicit Retry control restored the same session. Expiring only the disposable user's server-side refresh records led to sign-in. A replacement user did not see the prior account, and logout returned to sign-in. This verified definitive expiry handling through the refresh path.
- The dashboard screenshot had no app content. Android Recents showed the FINORA login task as a blank preview. The final debug app process produced 16,527 bytes of Logcat, with zero matches for the known synthetic credential/record markers or JWT pattern. This sample covers one process and no SMS parsing or release runtime. Those logging checks and the physical checklist stay open.
- Only Android 19 and Android 37.2 image packages are installed. Android 19 is an API level, below minSdk 26. Google's HTTPS command-line tools ZIP and SDK catalog both timed out in bounded connection attempts (curl 28, 8,001 ms and 8,005 ms) although DNS resolved. API 26 remains untested.
- A fresh-boot connected run reported one test failure while waiting for the registration screen. After the emulator was warm, with the screen awake and animations temporarily disabled, the unchanged connected suite passed: `Tests 2/2 completed. (0 skipped) (0 failed)`, `BUILD SUCCESSFUL in 38s`. The retry does not isolate the first run's cause. Animation settings were restored and read back.

## Authorized continuation from `62a70c3`

- D47 records Sahan's Android architecture and privacy approvals. D48 records his approval as device owner, conditional on Thilina's confirmed testing approval and the device safeguards. Thilina's approval of the exact handset invocation and the preflight evidence are still needed before use.
- The backend owner still needs to confirm STAFF provisioning and first-login policy, ticket redaction and retention/reassignment/deletion, server-side saved-reply review and diagnostic rules, ADMIN format mutations, and escalation access, lifecycle and retention. D45 remains the scoped delegation. The unresolved items still block implementation that would establish backend policy or require an unapproved contract.
- The three authorized Sinhala FAQ resource edits preserve 364 matched EN/SI resource pairs. The [consolidated native-speaker inventory](md05-sinhala-review.md) contains nine app pairs and four proposed saved replies. Prior approvals remain intact.
- Google's official SDK catalog and command-line tools ZIP again timed out before connecting (curl 28, 6,015 ms and 6,002 ms). API 26 remains unavailable. No HMD Crest commands ran.
- On the isolated emulator, the original registration test again timed out. An activity-lifecycle check found that Create account did not reach a resumed registration screen. Replacing only that tap passed on the warmed emulator, but the fresh-boot run then timed out waiting for the return to sign-in. The test now invokes registration control actions directly, checks both consent boxes, waits for the actual activity transitions, and checks that sign-in receives the email entered during registration. The registration and login still use the real disposable server. These checks exercise registration behavior independently of coordinate injection. Handset touch behavior remains on the physical checklist.
- Registration checkbox assertions use checked state. A checkbox can toggle even when `performClick()` returns false, so the return value is not used as a test assertion.
- The final revision of the registration checks passed both Android 17 connected tests on the running isolated emulator. A fresh boot of that final variant was not verified. T51.2, T51.3 and final acceptance remain unchecked.
- A fresh MySQL test schema applied all 13 migrations. Maven verification passed 324 tests including all 27 D39 cases, with the coverage gate met. The existing disposable schema also started at V13 with no pending migration. No migration files changed. Android JVM verification passed 140 tests, both APKs built, lint reported no issues, and the unsigned release passed the packaged security checks again. Static APK checks do not establish release-runtime or SMS Logcat behavior.

## Implementation under D49

- D49 records Sahan's report of both owners' verbal approval. D45–D48 remain the scope and safety baseline. No independent review or localization approval is recorded.
- T43 issues a 24-hour BCrypt-protected one-use activation credential. ADMIN can reissue a pending credential. The public activation operation takes the place of issuing a separate password-change token, since it grants no normal login, access token or refresh token. Session generations prevent old STAFF credentials from working after disable/re-enable. ADMIN and first-login Android screens are in place.
- T47/T48 add USER tickets, atomic STAFF claims, assignee replies and closing, and versioned bilingual templates. Input is restricted to canonical topic requests and approved reply blocks because unstructured text can carry raw SMS or credentials. Arbitrary ticket text and optional ticket error codes remain outside this slice. Staff review, wire privacy and end-to-end device evidence are tracked separately.
- T49 adds explicit consent on Paste SMS and inbox parse failures, with one stable error code and unknown metadata represented as JSON null. STAFF grouping and flagging and ADMIN acknowledgment/closure are implemented. The approved inbox scanner can attribute NSB as a bank code, without sending the observed sender. Format version remains unknown for an unmatched message. No financial fields or raw SMS are used by the report payload.
- T45 operation is limited to activation and disablement of the D35 hash-pinned NSB bundle. An unapproved or modified definition cannot be activated. General draft validation and rollback across separately approved versions remain open.
- T41.1 adds optional environment-based first-ADMIN bootstrap. MySQL tested that a repeat does not replace credentials or create a second ADMIN. The V17 migration adds a shared database lock for concurrent startup. The broader ADMIN dashboard remains open.
- The clean MySQL run applied V1–V17 and the upgrade steps V14 → V15 → V16 → V17 were exercised. Full Maven verification passed 334 tests, including all 27 D39 cases and a two-connection STAFF claim test, and met the coverage gate. The first V16 run found a stale JPA first-level cache in the MockMvc fixture; clearing it between simulated requests corrected the test setup.
- Android 17 verified a USER creating a support request, STAFF claiming a request and flagging diagnostic metadata, and ADMIN acknowledging and closing the flag and opening staff management. The live role smoke used the V16 API, which implements all of those routes. V17 adds only bootstrap. Provisioning creation and activation were verified through the MySQL integration test. Complete handset acceptance has not run.
- Final Android verification passed 142 JVM tests and both connected tests with no failures or skips. V1 debug and unsigned release assembled, and lint reported no issues. The Espresso fixture selector now uses the real Spinner adapter choice directly after an injected dropdown tap left a popup active. Interactive handset dropdown behavior remains a separate check. The release scan reconfirmed TLS, backup exclusions and debug-only READ_SMS and found no recognized credential signatures, key files or logger calls in 789 app-owned classes.
- T40 2/4, T48 2/4, T49 2/4 and T51 1/4, totaling MD05 7/16. Delegated T41 is 1/4, T43 2/4, T45 1/6, T46 2/3 and T47 2/5. No parent task moved to review or done. API 26, guarded HMD acceptance, release and SMS runtime Logcat, final native-speaker review and independent reviews remain open.
