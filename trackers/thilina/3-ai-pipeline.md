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
- [x] T26.5 D58: coaching FactKeys with units LKR/PERCENT/MONTHS, `slotLabels` limited to built-in names (custom → Other want/need), `factGuide` glossary in the prompt, schema up to 12 items

**Done when**
- [x] Test: FactSheet serialisation never contains fields outside the whitelist

**Handoff notes:**
- 2026-11-22 backend T25/T26 and catalogue explicitly approved by Thilina; code/doc schema share AdviceItem and FactKey. Missing facts omitted; labels are canonical, not potentially identifying category names.
- Privacy fixture proves actual transaction merchant/note/account/category injection and user details never enter serialized facts or prompts; other-user facts isolated.
- 12 advice/privacy tests passed on Java 17 + isolated `finora_test`; evidence `api/target/t25-t26-tests.log`. Schema/prompt are constraints, not a substitute for T29 verification.
- Next: review the feature PR; implement mocked providers and verification before any generated text can be delivered. No provider calls, keys, prompts or replies logged; T50 was not repeated.
- 2026-11-25 D58: FactSheet gained `slotLabels`; privacy fixtures confirm the injected custom category, goal name and merchants never reach the payload.

---

Execution 33 verified provider-disabled V1 delivered payload keys, decimal provenance and nullable IDs/ratings, plus matching Android DTO decoding. T26 review and dependency gates remain open.

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
- D44 live acceptance needs Thilina-approved model IDs, authorized `GROQ_API_KEY` and `GEMINI_API_KEY` in the isolated environment, and a numeric call budget.

---

### T28 · Gemini backup and rule fallback · `FR09`
**Lead:** Thilina · **Depends on:** T27 · **Touches:** `api:advice/ai/`, `api:advice/`
- [x] T28.1 HTTPS Gemini JSON-mode adapter; header key, explicit model, configurable unique provider order
- [x] T28.2 Transport failure → backup; both fail or verification rejects → canonical rule advice

**Done when**
- [x] Mocked failure paths end in valid advice; live acceptance remains pending

**Handoff notes:**
- 2026-11-22: Pipeline tests 13/13 PASS. Fallback implementation added. Full fallback acceptance and live integration pending.
- Final PR run: 51 focused / 253 full tests passed. T30 now delivers through the V1 USER endpoint. Backup, fallback and ownership were exercised with mocked providers. Live Gemini backup under D44 remains open.

---

### T29 · Number and claim verification · `FR09 / NFR-AIN`
**Lead:** Thilina · **Depends on:** T26, T27 · **Touches:** `api:advice/verify/`
- [x] T29.1 Provisional exact eligible text/number matching: LKR lossless to 2 decimals, no tolerance; return trusted canonical advice only
- [x] T29.2 Reject malformed/duplicate/extra fields, ineligible or duplicate actions, unsupported claims, wrong units/periods and fabricated values
- [x] T29.3 V8 owner-scoped verification events save verified flag and bounded reason; no raw text
- [x] T29.4 D58 free-wording check for coaching sections: every number matches a fact in order (key/unit/period), all eligible sections present (`MISSING_SECTION`), unsafe phrasing rejected (`UNSAFE_TEXT`)

**Done when**
- [x] Mocked verifier tests reject unsupported claims, fabricated values, wrong month/unit and extra precision; valid eligible replies pass

**Handoff notes:**
- 2026-11-22: Verifier tests 19/19 PASS. V8 migration validated. Final number-verification policy and real-world pass rate pending.
- Final PR run: 51 focused / 253 full tests passed; provisional exact-claim policy documented in docs/ai-schema.md. No tolerance, semantic verification or real pass rate approved; T29 stays in-progress pending team policy acceptance.
- T30 uses the existing verifier and preserves canonical fallback. Thilina's independent review covers the privacy allowlist, exact claims and live provider behavior under D44.
- 2026-11-25 D58: live Groq `openai/gpt-oss-120b` verified coaching for 2026-10 and 2026-11 on the synthetic account; one HTTP 400 fell back to rules; Gemini `gemini-3.8-flash` returned 503 each time. Unsafe-phrase list needs Thilina review.
