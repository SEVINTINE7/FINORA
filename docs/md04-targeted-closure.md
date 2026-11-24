# MD04 targeted closure

Baseline: `69977a1`. T38's open Done-when item is **“Every screen reachable; no stale data after edits (manual checklist in Handoff notes)”**. T09 remains in review. D40 permits the scoped Android work and preserves that formal prerequisite.

The [authorized closure evidence](md04-authorized-closure.md) records the subsequent decisions and verification against this baseline.

## Dependent-screen checks

The earlier [acceptance matrix](md04-final-acceptance.md) retains its per-form, import and historical transaction results. A supported financial dependency must reflect the new state when its screen reloads. Budgets are not inputs to Home, score or snapshot calculations. Account labels are used by pickers, not as aggregation inputs to Home or Insights. A saved V1 advice delivery freezes its content by contract, so changing financial inputs is not expected to replace that already delivered card. The current open month has no D27 snapshot.

| Dependent-screen row | Targeted evidence |
|---|---|
| Savings contribution add/remove → Insights goal credit | PASS. LKR 0.00 → 27.61 → 0.00 against a LKR 200.00 target, using the Android contribution form and confirmation. |
| Goal create/edit/cancel/resume/delete → Insights goal target | PASS. After resolving the prior no-goals declaration in the Android form, the target changed LKR 300.00 → 400.00 → unavailable → 400.00 → unavailable. |
| Bill paid/unpaid → Plan totals and Insights debt credit | PASS. Android paid toggles updated the Plan summary and Insights credited LKR 65.43 then LKR 0.00 against the same target. |
| Loan bill create/edit/delete → Insights debt target | PASS. The Android bill form changed the target LKR 54.21 → 73.46 → unavailable after confirmed removal. |
| Category rename and NEED → WANT → Home and Insights | PASS. Home showed the new category name and preserved LKR 50.25 total expenses. The reclassification changed Insights essentials from LKR 50.25 to LKR 10.00. |
| Score declaration → Insights score evidence | PASS. The Android form changed the reserve to LKR 200.35 and Insights showed the new exact value. |
| Score declaration for a closed month → History | PASS. After synthetic fixture setup, saving the October declaration through Android changed the existing snapshot from unavailable to 85/100. Its net stayed exactly LKR 959.75. |
| Closed-month loan paid marker → History | PASS. Marking the synthetic October bill paid in Android increased the existing snapshot score by 20 points. |
| Backdated goal contribution → History | PASS. Adding LKR 33.33 dated October 21 through Android increased the existing snapshot score by another 5 points. |
| Category reclassification → closed-month History | PASS. The Android form changed a synthetic category from WANT to NEED. History removed the high-wants finding and refreshed the October score from 80/100 to 76/100. Net stayed LKR 549.50. |
| Account rename and archive → new-transaction picker | PASS. It showed the renamed wallet, then stopped offering the wallet after an Android form action archived it. The Accounts list still showed its archived state. |
| Records transaction edit/delete → Plan budget usage | PASS. Budget usage changed LKR 10.00 → 15.17 → 0.00 after editing and deleting the expense in Android. |
| History and Advice recreation | PASS on isolated Android 17 emulator. History retained the selected 12-month range and returned to More. Advice retained the selected October month and returned to Insights. Both passed portrait–landscape–portrait. Physical recreation remains open. |
| Advice rating recreation | PASS on emulator. An unsaved 4/5 selection survived both orientation changes without becoming a saved rating. Explicit submission succeeded. The confirmed 4/5 was still shown after landscape recreation. |

The targeted emulator matrix closes the remaining identified dependent-refresh rows. Physical recreation and the device accessibility checklist below remain open. Keep T38 at 3/4 and MD04 at 19/20 while final acceptance is open. T20 stays 5/5, T24 4/4 and T30 7/7. T33 stays 5/5 separately, and MD01–MD03 stay 48/48. Formal dependencies and independent review remain open.

## Owner approval requested for rotation evidence

Request explicit approval from Sahan as T38 Android owner, with Sevindu's confirmation if accepting the alternative changes the agreed physical acceptance plan. The T38 tracker does not prescribe a phone model or OS for rotation. The acceptance plan specifically calls for physical rotation. The isolated HMD Android 14 profile could not enable Auto-rotate, and ADB recorded orientation 0. D34 approved a combination of physical and emulator evidence for T13.4 permission revocation and regrant only.

Proposed scope: retain HMD Android 14 evidence for touch navigation, Back, financial rendering and audible TalkBack. Accept isolated Android 17 landscape and portrait configuration changes only for recreation. Verify orientation from device configuration, selected tab and Back stack, transaction and declaration drafts, History range, Advice month and any displayed rating, and that recreation causes no duplicate writes. Record device type against each result. Physical rotation remains unverified. Approval would leave accessibility, localization, formal dependencies and independent review requirements in force.

## T33 catch-up decision proposed to Sevindu

This proposal governs recovery of missing older snapshots after an outage. Keep the ordinary previous-month D27 job and its existing-row refresh behavior.

- **Accuracy:** Recover missing months from the committed records available at execution. Mark recovered snapshots with provenance and computation time. Current edits, deletions, bill and goal settings, category classifications and declarations may differ from what existed at month close. Show recovered rows as reconstructed. Keep nullable scores when evidence is insufficient.
- **Eligibility and cutoff:** Use the first full Asia/Colombo calendar month whose start is at or after the approved production activation instant. Persist that exact `YYYY-MM` cutoff so restarts cannot move it. Include current USER accounts created before the first instant of the following Colombo month. Exclude deleted users and ADMIN/STAFF. Include eligible users with zero activity, with a null score where D20 returns one. Exclude the current open month.
- **Bounded recovery:** Propose a six-closed-month rolling maximum, never earlier than the approved cutoff. Work oldest first, up to 100 owner/month pairs per scheduled pass. Persist progress and retry failed owners independently. Report gaps outside the window for owner-directed recovery. No implicit reconstruction before the cutoff.
- **Concurrency:** Use the existing owner/month unique key and acquire the D39 owner row lock before reading inputs or writing a snapshot. Preserve existing clean snapshots. Keep the separate dirty-row, post-commit recalculation path and deletion cascade. Multiple instances may discover the same work. Recheck under lock before creating a row and use durable completion tracking so a crash resumes outstanding work.

Approval should specify the effective first month and approve or amend the proposed window and provenance contract. D27 continues unchanged until that approval is recorded.

## T30 live provider verification

The isolated backend needs the project-approved Groq and Gemini API credentials (`GROQ_API_KEY`, `GEMINI_API_KEY`) plus explicit approved provider model identifiers in `finora.llm.groq-model` and `finora.llm.gemini-model`. Enable only that backend with `finora.llm.enabled=true` and `finora.llm.provider-order=GROQ,GEMINI`. Thilina should authorize the exact IDs and the bounded test budget. No approved model IDs or live credentials were found in the authorized local configuration. Set secrets via the isolated process environment or ignored local configuration. Keep the app and production configuration free of test credentials.

Use synthetic eligible facts and fresh disposable monthly deliveries to verify Groq primary, Gemini backup on a controlled primary failure, canonical rule fallback on provider failure or rejected AI, and the aggregate-only outbound allowlist. Retain sanitized status, outcome and request-count evidence. Never record keys, request bodies, private identifiers or raw provider text.

## Focused accessibility device checklist

| Screen | Already verified on HMD | Remaining actual-device check |
|---|---|---|
| History | Sinhala trend heading, October total and score audible. Exact amount and Back reproduced. | In EN and SI, record TalkBack swipe order across title, selected range, previous/next, range buttons, chart heading and accessible monthly alternatives. Confirm missing score is spoken as unavailable. Verify Back focus restoration and clipping at larger text. |
| Advice | Both month controls audible in EN/SI. Saved rating audible in Sinhala. | In EN/SI, verify focus sequence through content, figures, guidance, rating choices, selected state, submit and saved or reconciliation feedback. Confirm actionable retry, sensible focus on return and no duplicate submission. Use a fresh disposable fixture for an unrated result. |

The 22-pair native-speaker sheet stays pinned to `11d80ed`. Both resource files still match that revision byte for byte. The sheet awaits approval. Existing 96- and 64-pair approvals remain intact.

This follow-up changes documentation only. The targeted UI checks ran on the isolated FINORA Android 17 emulator against the existing V12 MySQL-backed fixture API. The prior Maven and Gradle verification remains recorded in the [final acceptance report](md04-final-acceptance.md).
