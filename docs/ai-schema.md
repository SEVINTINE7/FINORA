# FINORA AI boundary (T25/T26)

Backend-only catalogue and continuation were approved by Thilina on 2026-11-22. No new financial thresholds:
D10 supplies the wants reference; D20 supplies savings target and reserve milestones. D03 is V1 only.

`FactSheet` serializes only `{month, facts, categoryLabels}`. Facts are numeric aggregates keyed by `FactKey`,
not transaction entities. Category labels are fixed NEED/WANT/SAVINGS, never user-entered category names.
No merchant, note, email, name, account reference, user id, goal name or bill name enters prompts.
Missing facts are omitted, not invented. RuleAdviceService loads the owner's metrics and score in a read-only
transaction. Stored bill/goal changes can affect recorded historical aggregates; these are not verified bank balances.

Rules return `[{actionId,text,numbers:[{factKey,value,unit,period}]}]`, also the required AI response shape.
See `ai-response.schema.json`; only actions eligible for this FactSheet may be returned, at most once each.
Amounts are LKR at 2 decimals. Every numeric token in rule text has an explicit fact reference.

| Action | Condition | Number provenance |
|---|---|---|
| REVIEW_SPENDING_DEFICIT | consumption > income | consumption - income |
| REVIEW_HIGH_WANTS | income > 0 and wants > 0.40 * income | wants; D10 reference (rounded to cents for text only) |
| REVIEW_UNMARKED_LOAN_BILLS | scored debt has unmarked bill amount > 0 | scheduled bills minus marked-paid bills; not proof of nonpayment |
| BUILD_EMERGENCY_BUFFER | declared reserve below next D20 milestone | declared reserve; next milestone times NEED expenses |
| REVIEW_GOAL_PROGRESS | capped goal credit < current target | target - separately capped contributions |
| SAVE_WITHIN_AVAILABLE_HEADROOM | income > 0, savings shortfall > 0, recorded leftover > 0, known debt applicability and no unmarked bills | min(savings shortfall, max(income - all expenses,0)) |

Savings facts never add goal allocations or reserve balances to Savings transactions. Existing savings allocations
are already in expense. Extra-saving suggestions are suppressed when debt evidence is missing/unmarked, and are
conditional on the user confirming unrecorded essentials/obligations. Goal advice reviews progress, not an order
to increase allocations. Essential-spending cuts, verified-payment claims, predictions and guarantees are forbidden.

Prompt: `AiPrompt.SYSTEM` is the fixed instruction; `AiPrompt.user()` serializes only FactSheet and eligible
server-generated rule actions. User-provided labels/notes/instructions are not interpolated. Do not log prompts or replies.
The schema is a provider constraint, not a verifier: T29 must still check strict fields, eligible action ids,
periods, units, text and numeric provenance before any generated text can be delivered. Until then, only rules
are suitable for delivery. Provider failure, invalid data or failed verification must fall back to those rules.
