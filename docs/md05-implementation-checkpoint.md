# MD05 implementation checkpoint

Baseline `b70c36a`. D45 and D46 record scoped delegation and early-start authority. Original ownership, formal dependencies and independent review still apply.

| Requirement | Implementation and remaining evidence |
| --- | --- |
| T40.1 | Existing encrypted token storage retained. Release artifact checks cover backup rules, cleartext and recognizable credential signatures. Runtime release logging evidence remains open. |
| T40.2 | Release backup disabled and token exclusions retained. Activity windows and app-owned dialogs use secure windows. Retain platform-specific screenshot and Recents checks in the device checklist. |
| T40.3 | Existing session and recovery paths retained. MD05 device lifecycle and expired-session matrix remains open. |
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

Tracked acceptance is T40 1/4, T48 1/4, T49 0/4 and T51 1/4, for MD05 3/16. Delegated T41 0/4, T43 0/4, T45 1/6 (retained T45.3), T46 2/3 and T47 0/5 are counted separately. T52 remains in progress with no new checkbox.

Android 8/API 26 was unavailable. The official SDK command-line tools download attempt timed out: `curl: (28) Connection timed out after 10006 milliseconds`. The connected run used Android 17 only. Physical instrumentation was not run.
