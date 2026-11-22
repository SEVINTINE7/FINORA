# Thilina 3 — AI advice pipeline (T26–T29)
Rules and workflow: see `CLAUDE.md` (rules 2 and 3 apply to every task here). Status lives in `trackers/INDEX.md`.
**Detail: expanded backend scope** — T27–T29 are preparatory, mocked-only and pending team acceptance.

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
- [x] T27.1 `LlmClient` and HTTPS Groq JSON-mode adapter; existing key property, explicit model, disabled by default; timeout 15 s
- [x] T27.2 Handle 429/5xx/timeouts/I/O with one retry then bounded failure result
- [x] T27.3 Log only request id, latency, status, token counts — never the prompt, reply, key or exception details

**Done when**
- [x] Tests with mocked HTTP: success, 429, timeout, invalid JSON

**Handoff notes:**
- 2026-11-22: Mocked HTTP client tests 19/19 PASS. Groq/Gemini clients implemented. Live provider, exact retry behavior and final security review pending.
- Final PR verification 2026-11-22: reconciled origin/main a0b5dfb without rewriting 72ec247/ed33c41. Mocked clients/verifier/pipeline 51/51 passed; full offline Java 17 verify 253/253, zero failures/errors/skips, exit 0; JaCoCo service lines 692/702 (98.58%), gate passed. Logs: api/target/final-pipeline-focused.log and final-pipeline-verify.log.
- Documentation now describes disabled configuration, explicit models, object envelope, retry and privacy contract. No live calls, credentials or provider acceptance; task remains in-progress pending review.

---

### T28 · Gemini backup and rule fallback · `FR09`
**Lead:** Thilina · **Depends on:** T27 · **Touches:** `api:advice/ai/`, `api:advice/`
- [x] T28.1 HTTPS Gemini JSON-mode adapter; header key, explicit model, configurable unique provider order
- [x] T28.2 Transport failure → backup; both fail or verification rejects → canonical rule advice

**Done when**
- [x] Mocked failure paths end in valid advice; live acceptance remains pending

**Handoff notes:**
- 2026-11-22: Pipeline tests 13/13 PASS. Fallback implementation added. Full fallback acceptance and live integration pending.
- Final PR run: 51 focused / 253 full tests passed after upstream reconciliation; backup, rule fallback and ownership exercised with mocked providers only. T30 delivery/Android integration and live acceptance remain separate; T28 stays in-progress.

---

### T29 · Number and claim verification · `FR09 / NFR-AIN`
**Lead:** Thilina · **Depends on:** T26, T27 · **Touches:** `api:advice/verify/`
- [x] T29.1 Provisional exact eligible text/number matching: LKR lossless to 2 decimals, no tolerance; return trusted canonical advice only
- [x] T29.2 Reject malformed/duplicate/extra fields, ineligible or duplicate actions, unsupported claims, wrong units/periods and fabricated values
- [x] T29.3 V8 owner-scoped verification events save verified flag and bounded reason; no raw text

**Done when**
- [x] Mocked verifier tests reject unsupported claims, fabricated values, wrong month/unit and extra precision; valid eligible replies pass

**Handoff notes:**
- 2026-11-22: Verifier tests 19/19 PASS. V8 migration validated. Final number-verification policy and real-world pass rate pending.
- Final PR run: 51 focused / 253 full tests passed; provisional exact-claim policy documented in docs/ai-schema.md. No tolerance, semantic verification or real pass rate approved; T29 stays in-progress pending team policy acceptance.
