# Sahan 2 — Manual entry and bank SMS reading (T11, T13, T14)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before coding.

---

### T11 · Manual transaction entry · `FR02 / FR03`
**Lead:** Sahan · **Depends on:** T10, T37 · **Touches:** `and:ui/transactions/`
- [ ] T11.1 Add/edit form: type, amount (LKR, 2 decimals), date picker, account, category, merchant, note
- [ ] T11.2 Field validation (amount > 0, required fields) with inline errors
- [ ] T11.3 Keep entered values on rotation and on API error; retry button
- [ ] T11.4 Works without SMS permission (always available from the main screen)

**Done when**
- [ ] Unit tests for validation; on device: rotation and airplane-mode error keep the values

**Handoff notes:** —

---

### T13 · SMS access and fallback · `FR03`
**Lead:** Sahan · **Depends on:** T37 · **Touches:** `and:ui/sms/`, `AndroidManifest.xml`
- [ ] T13.1 Note in Handoff: Play Store restricts READ_SMS → trial APK side-loaded (proposal 7.3)
- [ ] T13.2 Explanation screen before asking `READ_SMS`; clear "No thanks" path
- [ ] T13.3 Fallbacks always visible: paste SMS text, CSV import (T12), manual entry (T11)
- [ ] T13.4 Handle "Don't ask again" → link to app settings

**Done when**
- [ ] On device: grant, deny, deny-forever all lead to a working path

**Handoff notes:** —

---

### T14 · Local bank SMS parsing · `FR03`
**Lead:** Sahan · **Depends on:** T13, T45.3 (can start with bundled formats) · **Touches:** `and:sms/`
- [ ] T14.1 Read inbox via `Telephony.Sms.Inbox` filtered by known bank sender IDs and date range
- [ ] T14.2 `SmsParser` uses format templates (bundled JSON first, later fetched from `/api/v1/sms-formats`)
- [ ] T14.3 Extract amount, date, merchant, debit/credit; unknown format → "needs manual review"
- [ ] T14.4 Full SMS text stays on the phone — never in API calls or logs (rule 1)

**Done when**
- [ ] JVM unit tests for each supported bank format and an unknown format
- [ ] Network inspector shows no SMS body sent

**Handoff notes:** —
