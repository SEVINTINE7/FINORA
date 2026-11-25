# Android acceptance testing

Use this workflow for MD05, the final MD01–MD05 audit and subsequent Android testing. Keep the original acceptance criteria, device requirements and review gates.

## Prepare before involving the tester

1. Map each acceptance requirement to a scenario, expected result, required environment and evidence. Identify which checks need a person, such as confirming what TalkBack actually announces.
2. Use the isolated emulator, disposable accounts and synthetic fixtures for setup and repeatable checks. Verify the build, test account and API, navigation to the target screens, and the exact fixture states needed for feedback and recovery. Complete automated checks independently where possible.
3. For handset work, target the authorized device and confirm the existing isolated FINORA test profile before inspecting the UI or changing state. If the personal profile is active, restrict operations to identifying the active profile and switching to the approved test profile. Never inspect or use personal apps, accounts, SMS or data. Capture the starting profile, app locale, font scale, TalkBack settings and any temporary connectivity settings relevant to the planned changes.

## Run one consolidated session when practical

- Give the tester the complete checklist before starting. Include the screens, English and Sinhala order, standard and larger text, actions they need to perform, expected announcements and exactly what to report. Bring the phone to the first ready-to-test screen before asking for live participation.
- Group reading order, larger-text readability, focus restoration after Back or recreation, and accessibility feedback such as selected, saved, error or retry states. Use a single TalkBack activation across both languages when the test allows it. Automate navigation, fixture resets and locale or font changes whenever they are safe, authorized and reliable.
- Ask the tester only for actions or observations that automation cannot reliably supply. Avoid repeated requests to toggle TalkBack or perform the same action. Combine unresolved observations into one follow-up when possible. Run a separate focused session only when a confirmed fix, a changed requirement or a device limitation requires it. Record which check requires the extra session.
- Restore changed settings and the original profile at the end of each session, including after an interruption or failed test. Use only approved test-profile operations. Remove only temporary ADB mappings created for the session and preserve pre-existing mappings. Verify restoration with read-back checks and record any value that could not be restored or verified.

## Record evidence and audit independently

- Record the requirement, revision, device or emulator, locale, font scale, fixture, expected behavior, observed result and any remaining gap. Label evidence as automated, human-observed or both. ADB screenshots and UI trees can verify visible state and focus outlines. Audible announcements require genuine human observation. Record the tester's actual confirmation and leave ambiguous or unobserved outcomes unverified.
- Reuse valid evidence when the relevant source, fixture, contract and required environment still apply. Record what was reused. For the final MD01–MD05 audit, independently compare each original requirement with current implementation and evidence. Reverify required behavior using repeatable checks, and rerun the specific scenario when evidence cannot independently establish it. Tracker checkmarks and prior confirmations alone do not establish current correctness.
- Keep emulator and physical evidence distinct. Apply alternate environments only when explicitly approved for that requirement. Leave missing evidence open and keep independent review separate from technical acceptance.
- Enter disposable credentials through the in-process UI test harness. ADB shell command arguments can appear in system Logcat. Keep synthetic-input artifacts out of retained logs, and distinguish application-process logs from emulator and test-harness output.
