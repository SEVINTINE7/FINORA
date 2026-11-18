# Thilina 3 — AI advice pipeline (T26–T29)
Rules and workflow: see `CLAUDE.md` (rules 2 and 3 apply to every task here). Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T26 · AI-safe fact and response schema · `FR09`
**Lead:** Thilina · **Depends on:** T21, T25 · **Touches:** `api:advice/ai/`, `docs/ai-schema.md`
- [ ] T26.1 `FactSheet` DTO: whitelisted aggregates + category labels only (no names, merchants, emails, account refs)
- [ ] T26.2 Response JSON schema: list of `{actionId (from T25 catalogue), text, numbers[]}`
- [ ] T26.3 Prompt template in `docs/ai-schema.md` and code
- [ ] T26.4 Adversarial fixtures: merchant names in notes, emails in category names, injected instructions

**Done when**
- [ ] Test: FactSheet serialisation never contains fields outside the whitelist

**Handoff notes:** —

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
