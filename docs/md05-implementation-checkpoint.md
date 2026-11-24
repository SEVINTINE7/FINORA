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

## Closure work from `55d5cfc`

- T41 brings the five ADMIN-only aggregate counts into the dashboard without finance fields. T42/T44 functionality and corresponding dashboard links are still open.
- T47 adds optional allowlisted `error_code` to the existing privacy-safe ticket contract. It is available to the owner and assigned STAFF. The queue hides it before claim. The API still rejects free text, including identifiers and raw SMS.
- T45 adds NSB deployment-revision drafting, digest-gated immutable publication, and atomic activation, deactivation and rollback. V18 enforces one active revision per bank. The existing approved definition and all three synthetic NSB parser fixtures are unchanged. General parsing-rule authoring and validation of new definitions remain pending separate approved definitions and fixtures.
- Clean MySQL applied all 18 migrations and ran 336 tests. The V17 → V18 upgrade and full current verification ran 337 tests with no failures, including the 27 D39 cases. JaCoCo passed both runs.
- The first HTTP capture relay interfered with real Android writes. The final capture used a transparent loopback byte relay and retained only an exact-match verdict for the synthetic metadata payload. Local UI tests now use focused activity transitions and the actual app controls. The budget assertion uses its localized resource, correcting an encoding error in the earlier test expectation.
- API 26 is still unavailable. The official Google system-image catalog and official tools ZIP timed out during connection (curl 28, 6,005 ms and 6,003 ms). No HMD operations ran. The [consolidated checklist](md05-owner-proposal.md) remains the handset plan.
- Resource validation passed for 425 EN/SI pairs. The [review inventory](md05-sinhala-review.md) contains 70 app pairs and four saved replies. Earlier approvals remain unchanged.
- Final Android verification passed 142 JVM tests and all three Android 17 connected tests. Registration was reproduced after a cold boot and the suite passed after correcting the budget assertion. Both APK flavors built and final lint was clean. The packaged release scan covered 804 app-owned classes.
- T49 Android 17 verified Cancel followed by Confirm. The byte-forwarding relay observed exactly one three-field report per consent test, including explicit null bank and version. All three isolated database records used the expected unknown metadata and error code. STAFF grouped the UI-created report, ADMIN resolved it, and ADMIN also drafted, published, activated and rolled back revisions from the Android UI. MySQL verified duplicate handling, extra-field rejection and role denial.
- An offline copy of the unsigned release, signed with an ephemeral test key, launched on Android 17. Login was secure, screenshot content was blocked and READ_SMS was absent. The startup app-process log had no synthetic SMS or JWT matches. The isolated API and Android debug samples had no synthetic SMS or JWT matches. The guest system log had two synthetic password matches under `adbd` from ADB input during role smoke. Future runs must enter credentials in process. A trusted isolated HTTPS release origin is still needed for authenticated release runtime checks.
- Closure acceptance is T40 2/4, T48 2/4, T49 4/4 and T51 1/4, or MD05 9/16. Delegated T41 is 2/4, T43 2/4, T45 2/6, T46 2/3 and T47 3/5. Parent statuses remain in progress pending formal dependencies and independent reviews. T48 full device reply acceptance, broader ADMIN management, API 26, guarded handset testing and native-speaker review remain open.

## Targeted follow-up from `08a9f2b`

- T48: The Android 17 suite exercised the actual USER and STAFF screens. It created a ticket, verified that the catalog was initially empty, drafted a bilingual reply, rejected self-approval, approved with a second STAFF account, and previewed and explicitly sent each language. It retired the revision, opened a replacement draft and verified that the USER still reads both original replies. The API test also rejects USER and ADMIN catalog management and rejects arbitrary STAFF text. T48.1 and its Done-when test are verified. Handset TalkBack, larger text and native-speaker review remain pending.
- The connected suite needs an isolated debug emulator with SMS permission ungranted and a disposable MySQL API routed to debug loopback. The two runner arguments are `finoraIsolatedEmulator=true` and `finoraSupportFixtures=true`. The saved-reply test also requires the two synthetic STAFF identities specified by the test and an empty saved-reply catalog. Use a fresh disposable database for a clean run. Credentials are entered in process.
- Full Maven verification on isolated MySQL applied V1–V18 and passed 337 tests, including 27 D39 concurrency cases. The JaCoCo gate passed. Android verification retained 142 passing JVM tests, successful V1 debug and unsigned release builds, clean lint and four passing Android 17 connected tests.
- T51: The user approved the SDK license. Google's API 26 x86 system image (revision 1) matched the official archive size and checksum and booted as Android 8.0.0 on the isolated emulator. The first run found two instrumentation issues: Android 8 requires clipboard-service initialization on the main thread, and the edit test needed to wait for the transaction form and amount control. After those harness corrections, `connectedV1DebugAndroidTest` passed 4/4 with zero failures or skips. The API 26 and Android 17 command summaries and XML reports are retained separately. API 26 produced a zero-byte capture on FINORA login and a valid capture on the launcher control. T51.2, T51.3 and Done when remain open pending the recent handset run and its evidence.
- T40: The prior offline release run and packaged checks remain valid. Authenticated release checks still require a trusted isolated HTTPS origin for `FINORA_RELEASE_API_URL` with a certificate chain trusted by Android. The disposable API used for debug is HTTP loopback. The release trust configuration was left intact. No physical-device checks ran in this follow-up. The [consolidated handset checklist](md05-owner-proposal.md#one-consolidated-hmd-crest-session) requires preflight verification of an identical installed package or approval of an isolated test application ID.
- MD05 is 11/16 (T40 2/4, T48 4/4, T49 4/4, T51 1/4). MD01–MD04 retain 68/68. Delegated counts and INDEX statuses are unchanged. General ADMIN format authoring still needs approved definitions and fixtures for independent parsing-rule validation. Formal dependencies and independent reviews remain open. The [localization inventory](md05-sinhala-review.md) has 70 app pairs and four bilingual saved replies awaiting native-speaker approval. Earlier approvals still cover their original 183 pairs.

## D50 handset preparation

- The opt-in debug build produces `lk.finora.app.acceptance` and matching instrumentation. The ordinary v1 release build remains `lk.finora.app`, non-debuggable and without READ_SMS. The test runner checks the acceptance package and the nonzero Android user serial before reading or clearing a session. The Android 8 owner-profile check and the Android 17 wrong-serial check both rejected execution.
- The test APKs and recent handset procedure are described in the [D50 preflight](md05-owner-proposal.md#d50-package-and-runner-preflight). The opt-in build passed 142 JVM tests and lint reported zero issues. The separate Android 17 profile passed all four ADB-invoked scenarios in 74.172 seconds. The first attempt was blocked by the lock screen, and the next exposed form scrolling assumptions on the smaller emulator display. The successful rehearsal used an unlocked profile and focused test-only scrolling. Handset results, spoken labels and restoration remain separate evidence.
- The deployment guide gives a production URL example. T56 still records server and domain work as pending, and no authorized isolated TLS origin was configured. The disposable API binds to loopback over HTTP for debug. T40 needs an approved private/staging origin with a hostname-valid chain trusted by the unmodified Android release configuration and an isolated synthetic database. The existing production endpoint, certificates and trust settings were left intact.
- The [native-speaker inventory](md05-sinhala-review.md) was compared with both Android resource files and the four backend saved replies. It matches source revision `08a9f2b` exactly. The prior 183 approvals are preserved.

## D50 physical verification on HMD Crest

- The HMD Crest (Android 14, API 34) ran the originally prepared separate debug application and signed test APK from `ab9dd1e`. Both archive hashes and the shared signer matched. The existing FINORA test profile was user ID 10 with user serial 10. Direct ADB instrumentation reported `OK (4 tests)` in 60.513 seconds. It covered registration, sign-in, transaction and budget flows, the SMS-denied path, diagnostic confirmation and saved replies in both languages. The Android 8 emulator result remains separate.
- An opt-in in-process fixture established synthetic USER and STAFF logins for the physical checks. Dashboard and support-reply preview captures returned zero bytes. After disconnecting the temporary debug route and cold-starting the package, FINORA showed session-restoration failure and an explicit retry action. Reinstating the route and tapping retry recovered the dashboard. An app-process Logcat sample had zero JWT-like and synthetic diagnostic marker matches. These are debug-package observations.
- At 130% font scale the HMD accessibility tree exposed all five FAQ headings in the expected order, localized support status text and action descriptions, and both STAFF reply choices with full selected copy and a confirmation button in EN and SI. Back returned from FAQ and Support to More. Automation verified control bounds and the TalkBack window, but could not establish every node's restored accessibility focus, absence of visual clipping on a capture-blocked screen or the spoken output. Recents also returned no readable capture, so the physical preview contents remain unverified.
- The synthetic STAFF session was signed out and the acceptance login screen was verified. Test-profile settings read back as font scale 1.0, accessibility disabled, no enabled service and rotation unchanged. The temporary reverse mapping was removed and the original Android user was restored. The acceptance APKs remain in the test profile. The personal FINORA package was left intact.
- Retained test output is reproduced below. MD05 is 14/16 (T40 2/4, T48 4/4, T49 4/4, T51 4/4). MD01–MD04 retain 68/68. Delegated counts and INDEX statuses are unchanged. Formal dependency closure and independent review remain required.

  ```text
  Android 8.0.0 (API 26), connectedV1DebugAndroidTest
  Finished 4 tests on FINORA_MD05_API26(AVD) - 8.0.0
  BUILD SUCCESSFUL in 59s

  HMD Crest (API 34), isolated package, direct AndroidJUnitRunner
  lk.finora.app.acceptance.UserJourneyTest:....
  Time: 60.513
  OK (4 tests)
  ```

- The physical run used the isolated loopback API. Authorized infrastructure still has no approved release origin. A feasible private option is an owner-approved hostname with a system-trusted certificate issued through DNS-01, private resolution to a local TLS proxy, and a disposable backend restricted to the test network. This requires DNS owner authorization or an existing approved staging URL. The app trust configuration and device trust store were left intact.

## T40 closure checklist

**HTTPS authorization.** Provide an existing approved private staging HTTPS origin with a synthetic database and a route from the isolated test device, or authorize one DNS name under a domain the team controls, a DNS-01 TXT record or delegated challenge record, certificate issuance for that name, and private DNS and network access to a local TLS proxy. Only the DNS challenge needs public validation. The proxy and API stay private. Supply the origin and access instructions separately from any secrets. The deployment example is not staging authorization, and no release origin was present in the Windows process, user or machine configuration.

**Release checks after setup.** Verify the certificate chain and hostname from the isolated test environment with the standard Android trust store. Build the non-debuggable V1 release using `FINORA_RELEASE_API_URL` and sign it with a disposable test key for an isolated emulator. Keep the D50 debug APK for physical checks. Enter synthetic credentials in process. Verify a fresh login, refresh after access-token expiry, rejection after refresh expiry, logout, replacement-login isolation, and recovery after an interrupted connection. Use synthetic text for Paste SMS and capture only diagnostic request field names and match counts. Confirm no raw text or credential values reach requests or app logs. Retain sanitized results tied to the APK hash and test environment before closing T40.1.

**One handset session.** Pre-stage the D50 app and synthetic USER and STAFF fixtures. Keep TalkBack on through both languages and test at 130% text. Ask the listener to confirm the spoken heading, ticket status, reply and confirmation, the actual first focused item after Back, and any clipped lines. Inspect the dashboard and support Recents cards for exposed content. Automate the expired-session return to sign-in and the replacement-account check, including correct ownership of the displayed fixture. Restore the starting profile, font, TalkBack and temporary route. Record each spoken or visual result separately from automated node evidence. Close T40 Done when only after the handset checklist and sanitized logging evidence pass.

**Other gates.** The exact [native-speaker inventory](md05-sinhala-review.md) is pinned to `08a9f2b` and contains 70 app pairs and four bilingual saved replies. Await explicit approval for those values. MD05 remains 14/16, T40 remains in progress, and INDEX and formal review gates are unchanged.

## D51 local verification

The isolated HTTP development API and MySQL passed refresh rotation and old-token rejection, rejection after moving exactly one synthetic refresh row's expiry into the past, cross-account list and direct-read isolation with replacement credentials, diagnostic field allowlisting and logout refresh rejection. The isolated server log sample contained 49 lines, zero JWT-like matches and zero occurrences of the rejected synthetic SMS marker. This is API evidence. The prior Android emulator session and handset debug evidence remain separate.

Authenticated release HTTPS verification resumes during [T56 deployment](../trackers/sevindu/7-testing-release.md) and remains part of T40.1. The consolidated handset checklist remains open for audible and visual checks and physical session evidence. No new checkboxes are supported by the local API results alone.

## D51 handset session follow-up

On the HMD Crest, the D50 isolated debug app displayed the first synthetic account's wallet. Moving exactly one active refresh row's expiry into the past caused a cold restart to show “Please sign in again.” A fresh synthetic login displayed an empty account list without the first user's wallet. These checks used the local API and physical test profile. No prior functional suite was rerun.

An opt-in layout probe on the handset passed (`OK (1 test)`, 8.507 s). With font scale 1.3 in both English and Sinhala it checked all nine FAQ text views, heading semantics, layout height, line width excluding trailing whitespace, and zero ellipsized characters. This verifies the FAQ text layout. Exact TalkBack focus after Back, audible labels and readable content in physical Recents remain unverified. The original user, 100% font size and disabled TalkBack were restored, and the temporary ADB route was removed. T40 remains 2/4 and MD05 remains 14/16. D51 still defers authenticated release HTTPS verification to T56.

## Physical Recents and Back focus follow-up

- On the HMD Crest with the D50 debug package, Android's task snapshot cache reported `mIsRealSnapshot=false` for the FAQ, dashboard and Support after opening Recents. This is system evidence that the three cards used placeholders in place of app pixels.
- With TalkBack left enabled, a targeted accessibility probe focused Help, opened the FAQ and pressed Back. Android reported accessibility focus on **More** in English and **තවත්** in Sinhala. Both probes failed the expected return to Help. Keep the focus requirement open. The probe patch and the sanitized outputs are kept with the local review evidence.
- Spoken output remains pending human verification. No human observation was supplied. Test-profile font scale and TalkBack were restored to their starting values, the temporary route was removed, and Android user 0 was verified. No previously passed suite was rerun. T40 remains 2/4, MD05 remains 14/16 and authenticated release HTTPS stays under D51/T56.

## FAQ Back focus correction

Help and Support launched as ordinary activities, while only History and Advice registered the originating button for restoration. USER and STAFF home screens now share the existing return-focus flow for these routes. It restores the originating button after the returned window settles, checks that touch exploration is enabled and the button is visible, and cancels on intervening input or a window change. Input focus alone left TalkBack on the previously focused Help button when returning from Support, so the delayed restore also targets accessibility focus.

With TalkBack enabled on the isolated Android 17 emulator, two targeted instrumentation tests passed in 27.383 seconds. They verified USER Help and Support in EN/SI and STAFF Support in EN. The V1 debug and test APKs built, and lint reported zero issues. The previous physical results still describe the original build. Physical correction and spoken labels require the final handset session. MD05 remains 14/16, T40 stays in progress, and D51/T56 authenticated release verification remains open.

## Final autonomous handset verification

- The corrected D50 package passed both focused tests on HMD Crest Android 14 in 22.250 seconds. The tests observed accessibility focus return to Help and Support in both languages and to Support from STAFF Home. The separate FAQ layout test passed in 8.271 seconds after the Sinhala wording correction, with headings, line bounds and zero ellipsized characters at 130% in both languages. The V1 debug and test APKs built and lint reported zero issues.
- The corrected handset build again recorded non-content task snapshots for Dashboard and Support in Android Recents. TalkBack emitted audio-focus records marked accessibility speech. These records do not contain spoken words. Android's public playback capture is limited to media, game and unknown usages, so exact spoken labels remain pending human verification. The synthetic session was signed out, test-profile font and TalkBack settings were restored, the temporary route was removed and Android user 0 was verified.
- The 70 Android pairs and four backend reply pairs were reviewed at `7339893`. Three Sinhala values were corrected. XML resource names, plural quantities and format arguments matched across 425 entries. The review inventory matched source; the earlier 183 approvals were not extended. Native-speaker approval remains open.
- MD05 remains 14/16 and overall technical evidence remains 82/84. T40.1 requires D51/T56 authenticated release verification, and the handset manual checklist still needs audible confirmation. T46 is 3/3 based on retained MySQL role coverage and T47 is 4/5 based on physical USER Help and STAFF ticket screens. T41 stays 2/4, T43 2/4 and T45 2/6. T41 lacks the T42/T44 links and complete expired-token endpoint evidence, T43 lacks verified device provisioning and exact disabled-session outcomes, and T45 needs approved format authoring and validation fixtures. T47's literal redaction acceptance remains open because the approved canonical path rejects free text rather than returning redacted text. T52 remains owner-led. INDEX statuses, dependencies and independent reviews remain unchanged.

## Pre-delivery evidence and handoff

Targeted MySQL verification passed 3/3 for the ADMIN stats and STAFF provisioning controllers. The isolated Android 17 provisioning test passed in 21.457 seconds and the bilingual registration consent-gate test passed in 5.115 seconds. Android build and lint passed. T41 is now 3/4 and T43 is 4/4. T45 remains 2/6, T46 3/3 and T47 4/5. T07.4 gained emulator evidence. MD05 remains 14/16 and overall acceptance remains 82/84.

See the [delivery handoff](md05-delivery-handoff.md) for the exact localization inventory, one TalkBack session, the private HTTPS authorization and the task-owner review sequence. No INDEX status, API contract or decision was changed.

## D52 delegated gap closure

T47 strips a single bounded labeled account/card line before storage. Android sends the canonical body and the API rejects arbitrary content. MySQL verified storage, safe owner/assignee disclosure, role isolation and unsafe-input rejection. T47 is 5/5.

T45 lets ADMIN discard unpublished inactive NSB drafts. MySQL verified roles, published-revision protection, invalid pattern rejection, rollback and byte-identical USER responses across activation and rollback. Android parsed all three D35 synthetic examples with the exact V9 deployment definition, and the selected T16/parser regressions passed. T45 is 3/6. Arbitrary editable rules and per-sample validation remain outside the pinned deployment workflow.

Focused results were 13/13 MySQL tests and 19/19 Android JVM tests. T41 stays 3/4 because T42/T44 implementations are missing. T43 remains 4/4 and T46 3/3. Delegated checks are counted separately. MD05 remains 14/16 and overall acceptance remains 82/84. No INDEX status changed. The existing [one-session checklist and owner handoff](md05-delivery-handoff.md) retain the spoken, localization, release HTTPS and independent review gates.

## Final localization and diagnostic verification

- The exact 70 application pairs and four unchanged saved replies are pinned to `a9c54667a0f71b8b646fa8405405cfcd06819e00`. Six requested resource keys changed. All 425 entries match across languages, including format arguments and plurals. The previous 183 approvals retain their original scope.
- Neither Android diagnostic report path identifies a parser version. Paste submits null bank and version, and inbox can submit NSB with null version. The preview's “unknown” stays accurate. JVM tests cover all four error codes with both permitted bank states and exactly three wire fields. Isolated Android 17 verified the full localized preview, Cancel then explicit Send, and measured text bounds without ellipsizing in EN/SI at 130% text.
- Android ADMIN can confirm discard of an unpublished NSB draft. Android 17 verified deletion and the continued presence of active revision 1. MySQL rechecked immutable publishing, rollback, diagnostic authorization and bounded ticket redaction. The synthetic identifier was absent in captured logs and audit reasons.
- Focused results were 12/12 MySQL tests, 7/7 Android JVM tests, the Android draft-discard test and both bilingual preview layout runs. V1 debug, unsigned release and test APKs built. Lint was clean. The release APK remains non-debuggable with no READ_SMS permission. Existing physical evidence remains valid for the unchanged features.
- T41 remains 3/4 pending Sevindu's T42 and T44 implementations. T45 remains 3/6 pending approved editable definitions and the sample-validation and authoring contract. T43 is 4/4, T46 is 3/3 and T47 is 5/5. T07.5 remains blocked by T36, which depends on T35. T40 is 2/4 and MD05 14/16. The [handoff](md05-delivery-handoff.md) retains the listening sequence, native-speaker approval, D51/T56 HTTPS and independent review gates. INDEX statuses are unchanged.

## D53 dependency implementation and verification

- No incoming changes or alternate remote branches were present. D53 records Sevindu's delegated implementation authorization as relayed by Sahan. The audit reason allowlist, schema-1 export and local-only sample editor are implementation choices for owner review.
- T41 is 4/4. T42 and T44 are each 4/4 with live ADMIN dashboard links. T35 is 3/4 and T36 is 5/5. Settings now exposes export and password-confirmed deletion. T45 remains 3/6. Its local editor validates synthetic samples with the USER parser, while the backend still only publishes immutable approved bytes. This does not fulfill persisted editable definitions or mandatory pre-activation sample validation.
- Targeted MySQL passed 65/65 including all 27 D39 cases. Three focused tests were rerun with transaction/budget/goal role checks and export child-row isolation, and passed. Android JVM passed 8/8 and two isolated Android 17 journeys passed. The system picker wrote valid synthetic JSON. V1 debug, test and unsigned release builds passed and lint reported zero issues.
- The exact MD05 inventory at `a9c54667a0f71b8b646fa8405405cfcd06819e00` remains unchanged. The new dependency screens add 29 separate EN/SI pairs pending review. XML and format parity passed across 453 entries. One obsolete deletion placeholder was removed.
- MD05 is 14/16 and overall 82/84. The original five delegated tasks are 19/22. T35, T36, T42 and T44 moved from todo to in progress. Ownership, formal prerequisites, native-speaker review, audible TalkBack, and authenticated release HTTPS remain open where applicable.

## D54 restricted consent and format authoring

- V20 persists NSB drafts and binds validation to the candidate and server-owned fixture revision. Isolated Android 17 verified ADMIN draft edit/save, per-sample results, publication, activation and rollback. Approved D35 definitions remain the only publishable content.
- V21 adds single-use consent recovery for password-authenticated withdrawn USER accounts. The isolated Android 17 journey verified withdrawal, restricted sign-in, cancellation in English, explicit restoration in Sinhala at 130% text and fresh sign-in. The test profile font scale was restored. Backend verification passed 351/351 tests, including 27/27 D39 cases and concurrent consent acceptance and format activation.
- Android JVM passed 146/146. V1 debug, test and unsigned release builds passed with zero lint issues. The release APK is non-debuggable, excludes READ_SMS and disallows cleartext. Authenticated release HTTPS remains pending under D51/T56.
- T45 technical checks are 6/6 within the approved NSB scope. MD05 remains 14/16 and overall technical acceptance remains 82/84. The combined HMD Crest spoken TalkBack and consent session, native-speaker approval, formal prerequisites and independent reviews remain open.
## D55 T44 financial semantics

- Isolated MySQL reproduction unchanged downstream: an ADMIN rename changed T21 savings from LKR 12.34 to 0.00 and caused T23 to accept a no-debt declaration that it rejected before renaming a category with a recorded loan expense.
- V22 upgrade from V21 preserved two renamed original rows, an inactive savings flag, one exact-LKR transaction and the linked budget. A new category reusing the old Savings name stayed untagged. A broken seed cohort was rejected before the semantic column existed.
- T44 now supplies the read-only codes. Thilina’s T21/T23 query changes and integrated history and advice verification are pending. See the [shared database contract](db-schema.md#v22-default-category-semantics-d55) and [handoff](md05-delivery-handoff.md#d55-financial-semantics-handoff).
- MD01–MD04 remain 68/68, MD05 14/16, overall 82/84 and original delegated scope 22/22. The D50 APK pair is unchanged and ready for the consolidated handset session. The 106 pending language pairs and four replies are unchanged.
## D50/D54 HMD Crest consent acceptance

- The existing Android 14 isolated profile ran the prepared D50 acceptance and instrumentation APKs with matching signers. Only the acceptance package was installed with replacement. The loopback API used an isolated synthetic MySQL database.
- Direct guarded instrumentation returned `OK (3 tests)` in 19.992 seconds. It verified the EN/SI two-checkbox gate, system-picker export and password-confirmed deletion, and withdrawal through restricted sign-in, cancellation, explicit re-consent and a fresh normal sign-in. A separate Settings interaction verified the UI changed to Sinhala and the API persisted the new language without changing the synthetic profile name.
- Server-side reads confirmed a revoked old refresh row, a new active refresh row, a new version 1.0 consent and no remaining recovery grant for the physical run. No credential value was captured. The app's secure window was observed and 326 UID-scoped acceptance-app log lines contained zero credential or SMS pattern matches.
- TalkBack was enabled once in the test profile and bound with spoken feedback. Sinhala USER FAQ and the assigned STAFF ticket with both saved-reply choices and a confirmation preview were staged. The listener acknowledged availability but did not confirm any specific spoken output. Node and service evidence do not establish audibility. The T40 device checklist remains open, as does authenticated release HTTPS.
- Signed out of isolated FINORA and verified the login screen. Restored TalkBack disabled with an empty service list, verified font scale 1.0, removed the temporary USB reverse mapping and returned to original Android user 0. The acceptance APKs remain available in the isolated profile.
- T07 is 7/7 and in review. MD01–MD04 remain 68/68, MD05 remains 14/16 and overall acceptance remains 82/84.
## Final MD05 Sinhala wording approval

- Sahan explicitly approved the 106 app resource pairs and four existing bilingual STAFF replies on 2026-11-25, subject to the exact corrected wording committed at `80d5c200bb1464f6de80742e5363f6d6b72f4f95`. All other approved values are unchanged. The earlier 183 approvals and the second-STAFF operational publication requirement retain their scope.
- XML, names, format arguments and plural forms matched for all 460 resource entries per locale. All 106 inventory rows and four reply pairs matched the exact source values.
- The isolated Android 17 test at 130% font scale verified the corrected re-consent dialog title, restoration feedback and permanent-deletion message fit without clipping or ellipses. Direct instrumentation reported `OK (1 test)` in 35.868 seconds. D50 app and instrumentation APKs built and lint found no issues. The successful HMD consent tests were reused.
## Final HMD spoken TalkBack confirmation

- One Android 14 D50 listening session used isolated synthetic USER and STAFF fixtures and one TalkBack activation. Automation advanced the two languages, FAQ and Support navigation, STAFF preview, offline Retry and expired-session feedback. The synthetic reply was left unsent.
- Sahan confirmed: “All listed labels were audible and correct in both languages.” The checklist covered USER FAQ title and first question, Help after Back, Support entry/status/View replies and Back focus, STAFF status/reply choices/preview/confirmation and Back focus, and Retry/Please sign in again. The confirmation comes from the listener.
- App-scoped Logcat contained zero credential or SMS pattern matches in the eight available lines from the listening pass. Earlier isolated consent evidence contains 326 clean lines. No consent test was repeated on the handset.
- The isolated app returned to its signed-out login. Original per-app locale `si`, 121000 ms screen timeout, 1.0 font scale, disabled TalkBack with no enabled services, empty reverse mapping and original Android user 0 were verified after restoration.
- T40 is 3/4 and remains in progress. MD05 is 15/16 and overall MD01–MD05 is 83/84. D51/T56 authenticated release HTTPS, formal prerequisites and independent human reviews remain open. No approved private Android-trusted HTTPS origin was supplied or configured for this session.
