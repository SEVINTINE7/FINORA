# FINORA AI boundary (T25/T26, D58)

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
The provider schema constrains the response. The T29 verifier checks strict fields, eligible action IDs,
periods, units, text and numeric provenance before delivery. Provider failure, invalid data or failed
verification falls back to canonical rules.

## D58 goal coaching (2026-11-25, pending Thilina review)

Five coaching sections come before the catalogue actions, each only when its facts exist:

| Section | Facts (all server-calculated) |
|---|---|
| GOAL_PLAN | user-selected focus goal (`goalId`): target, saved, remaining, months left, monthly amount needed, three-month pace, months at pace and with the salary plan |
| CUT_WANTS | top three want categories: this month, prior three-month average, suggested cut = max(25%, excess over average) capped at 50%, whole rupees; total cut; wants % of income |
| NEEDS_BEHAVIOUR | needs % of income; top two need categories and their average. Needs are never suggested for cuts |
| SPENDING_PATTERN | spending in days 1–10, 11–20 and 21–end; % in the first ten days |
| SALARY_PLAN | needs (all non-want consumption), wants limit = min(wants − cuts, 40% of income), weekly wants, goal transfer = min(monthly needed, income − needs − wants limit), buffer |

`FactSheet` adds `slotLabels` (`TOP_WANT_1..3`, `TOP_NEED_1..2`) restricted to built-in English category names;
custom categories are grouped as "Other want"/"Other need". Goal names stay on the server. Units are `LKR`,
`PERCENT` or `MONTHS` (whole numbers). The prompt adds a fixed `factGuide` glossary.

The LLM may word coaching sections freely. The verifier requires every eligible section exactly once, rejects
investments, borrowing, guarantees, links and cuts to need categories, and requires the numbers in the text
(thousands separators allowed) to equal, in order, the listed facts with the right key, unit and period. Accepted
sections keep the provider wording with server-built number objects. Catalogue actions keep the exact-text policy below.
Delivery is frozen per user, month and focus goal (V24). Coaching wording is English; app headings are EN/SI.

## Preparatory T27–T29 integration (pending team acceptance)

HTTP transports request an object containing only `advice`, whose array uses the item schema above.
The verifier also accepts the canonical array directly. This transport envelope does not change AdviceItem.
The provisional policy permits only exact eligible rule text and exact numeric provenance; AI may select
and reorder eligible actions, but paraphrases, additional claims and fabricated numbers fall back to rules.
Accepted objects are rebuilt from trusted rules, never returned directly from provider JSON.

Providers are disabled by default (`finora.llm.enabled=false`). Future explicit enablement requires
`finora.llm.groq-api-key` / `finora.llm.gemini-api-key` and corresponding `groq-model` / `gemini-model`
properties; no model is selected automatically. Existing environment mappings remain unchanged.
`finora.llm.provider-order` defaults to GROQ,GEMINI. Each attempt has a 15-second timeout; 429, 5xx,
timeout and I/O failures receive one retry. Transport failures try the backup; rejected advice goes
directly to rules. No eligible actions or disabled providers require no provider traffic.

Only generated request id, latency, bounded status and token counts are logged. V8 stores owner/month,
provider, verified flag, bounded reason and UTC creation time, never prompts or rejected text.
T30 delivers the verified canonical result or rule fallback through the [USER-only V1 contract](api-contract.md#d41-monthly-history-and-v1-advice). Provider verification used mocked HTTP. D44 live verification still needs Thilina-approved Groq and Gemini model IDs, authorized isolated credentials and a numeric call budget. Live-provider acceptance, provider privacy capture and a measured live pass rate remain open.
