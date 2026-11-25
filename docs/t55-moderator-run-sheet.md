# T55.3 moderator run sheet

Use with the participant information, consent, six-task script, and SUS scoring instructions in [evaluation.md](evaluation.md). This run sheet is ready for an approved pilot. Confirm the study protocol with the team and lecturer before recruiting or collecting participant data.

## Before the session

Record the protocol version, approved participant information and consent revision, app and API commits, fixture version, locale, and device profile. Confirm the study contact, eligibility, withdrawal deadline, retention period, task limits, and SUS acceptance statistic against the approval record. Stop setup if any required decision is missing.

Rehearse the six tasks with a separate synthetic account. Write the expected app state for each task on the moderator-only scenario card. Prepare synthetic starting states for later tasks so a failed earlier task does not determine a later outcome. Use an isolated provider-disabled API. Allocate a random participant code and keep any required consent record separately.

## Run order

1. Read the participant information, allow questions, and document consent before starting observations. A refusal ends the session.
2. Read one task verbatim from the evaluation protocol. Start the timer when the participant begins. Stop at the success criterion, the agreed time limit, or a participant decision to skip. Record the outcome before restoring any starting state.
3. If asked for help, give the same neutral prompt first. If task-specific help is needed, record the approved hint and classify a successful outcome as assisted. Record technical failures separately and retain the original attempt if a retry is authorized.
4. Give the standard ten-item SUS form after the tasks and before debriefing. Use one answer per item. Invite the participant to resolve ambiguous marks voluntarily. Leave unresolved items missing.
5. Debrief, provide the withdrawal instructions and participant code, and store coded records in the approved location outside Git. Exclude incomplete forms from SUS scoring and have a second reviewer verify scores and exclusions.

## Blank records

Observation header

```csv
protocol,participant_code,task,attempt,fixture,locale,app_commit,api_commit,outcome,neutral_prompt_count,hint_code,elapsed_ms,missing_time_reason,user_error_code,technical_incident_code
```

SUS header

```csv
protocol,participant_code,locale,q1,q2,q3,q4,q5,q6,q7,q8,q9,q10,valid_items,missing_reason,sus_score,second_review
```

Use the outcome categories defined in the evaluation protocol. Report participant flow, task outcomes and assistance with denominators, and valid versus excluded SUS forms before presenting score summaries. Keep free text free of identifying details.

## T55.4 separate protocol

Keep advice comparison outside the V1 app runtime and this SUS session. Before collecting comparison data, obtain an explicit decision on whether to run it. If approved, predefine the paired unit, primary outcome, randomization and blinding, exclusions, handling of unavailable or fallback advice, analysis, and reporting. New provider requests require reconciled billing and separate explicit authorization.
