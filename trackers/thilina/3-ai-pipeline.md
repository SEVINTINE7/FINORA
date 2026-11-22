# Thilina 3 — AI advice pipeline (T26–T29)
Rules and workflow: see `CLAUDE.md` (rules 2 and 3 apply to every task here). Status lives in `trackers/INDEX.md`.
**Detail: T26 expanded below; T27-T29 remain outline** — expand each remaining task before coding.

---

### T26 · AI-safe fact and response schema · `FR09`
**Lead:** Thilina · **Depends on:** T21, T25 · **Touches:** `api:advice/ai/`, `api-test:advice/`, `docs/ai-schema.md`, `docs/ai-response.schema.json`
- [x] T26.1 `FactSheet {month,facts,categoryLabels}` DTO: FactKey enum whitelist + fixed NEED/WANT/SAVINGS labels only; factory reads numeric aggregates, never user-entered labels/entities
- [x] T26.2 Strict response JSON schema: list of `{actionId,text,numbers[{factKey,value,unit,period}]}`, no extra fields, at most six eligible catalogue actions
- [x] T26.3 Fixed system prompt + serialized whitelist/eligible actions in `AiPrompt`; documented safety and missing-data contract
- [x] T26.4 Adversarial fixtures include actual database merchants/notes, email/instruction category names and cross-user source isolation

**Done when**
- [x] Test: FactSheet serialisation never contains fields outside the whitelist

**Handoff notes:**
- 2026-11-22 backend T25/T26 and catalogue explicitly approved by Thilina; code/doc schema share AdviceItem and FactKey. Missing facts omitted; labels are canonical, not potentially identifying category names.
- Privacy fixture proves actual transaction merchant/note/account/category injection and user details never enter serialized facts or prompts; other-user facts isolated.
- 12 advice/privacy tests passed on Java 17 + isolated `finora_test`; evidence `api/target/t25-t26-tests.log`. Schema/prompt are constraints, not a substitute for T29 verification.
- Next: review the feature PR; implement mocked providers and verification before any generated text can be delivered. No provider calls, keys, prompts or replies logged; T50 was not repeated.

---

### T27 · Server-only Groq integration · `FR09`
**Lead:** Thilina · **Support:** Sevindu · **Depends on:** T04, T26 · **Touches:** `api:advice/ai/`
- [ ] T27.1 `LlmClient` interface; `GroqClient` (key from `GROQ_API_KEY`, timeout 15 s, JSON mode)
- [ ] T27.2 Handle 429/5xx/timeouts with one retry then failure result
- [ ] T27.3 Log only request id, latency, status, token counts — never the prompt or reply

**Done when**
- [ ] Tests with mocked HTTP: success, 429, timeout, invalid JSON

**Handoff notes:** —

---

### T28 · Gemini backup and rule fallback · `FR09`
**Lead:** Thilina · **Depends on:** T27 · **Touches:** `api:advice/ai/`, `api:advice/`
- [ ] T28.1 `GeminiClient` behind same interface; provider order configurable
- [ ] T28.2 Groq fails → Gemini; both fail or check fails → rule-based advice (T25)

**Done when**
- [ ] Tests for each failure path end in valid advice

**Handoff notes:** —

---

### T29 · Number and claim verification · `FR09 / NFR-AIN`
**Lead:** Thilina · **Depends on:** T26, T27 · **Touches:** `api:advice/verify/`
- [ ] T29.1 Extract every number in AI text; each must match a fact (rounding rule documented, e.g. ±0.5 for %, exact LKR to 0 dp)
- [ ] T29.2 Reject unknown `actionId`s, wrong units/periods, numbers not in facts
- [ ] T29.3 Save `verified` flag + reject reason (no text) for pass-rate reporting

**Done when**
- [ ] Tests: fabricated number, changed %, wrong month, rounding edge — all rejected; valid reply passes

**Handoff notes:** —
