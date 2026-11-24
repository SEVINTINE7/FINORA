# MD04 authorized closure evidence

Baseline: `474e291`. D42 records the approved split between HMD Crest Android 14 device evidence and isolated Android 17 emulator recreation. D43 governs bounded T33 recovery. D44 permits bounded live-provider verification when the approved configuration is available. Original task ownership and independent review requirements are unchanged.

## T33 recovery

V13 stores the activation-derived first full Colombo month, reconstruction timestamps and durable per-owner retry eligibility. The worker examines the older part of the six closed months in chronological order and attempts up to 100 owner/month pairs per pass. The ordinary previous-month job and dirty snapshot refresh still run independently. Recovery uses D39 locking and checks current role, account creation time and snapshot existence under the lock. Completed rows survive restarts, failed pairs are deferred for 30 minutes, and user deletion cascades to snapshots and retry rows. Existing clean snapshots and retained provenance survive refresh. Reported out-of-window ranges require owner-directed review.

The MySQL verification included both V12→V13 upgrade and a clean V1→V13 schema. Full Maven verification passed 322 tests, including five recovery regressions and all 27 D39 concurrency regressions. The JaCoCo gate passed. The running isolated V13 scheduler recovered a synthetic September row with exact net LKR −9.77, a null score and UTC reconstruction provenance. HMD Sinhala History displayed the notice and exact value at font scale 1.3.

## HMD and emulator evidence

Retain the 14/14 [targeted emulator rows](md04-targeted-closure.md) and earlier [form and device evidence](md04-final-acceptance.md). The HMD test profile still cannot perform physical Auto-rotate. D42 accepts the emulator configuration changes for that specific recreation requirement. The recreation evidence covers selected tabs, Back, transaction and declaration drafts, History range and Advice month and rating state.

On HMD Android 14 in the isolated test profile, Sahan confirmed English and Sinhala History reading order, including the chart heading and unavailable score. Larger-text English History controls remained visible. Sahan confirmed Sinhala History wrapped and remained readable at 1.3. English and Sinhala Advice rating options, selection, submit and saved state were checked at 1.3. ADB verified the final 4/5 value after an explicit synthetic submission in each language, and Sahan confirmed the resulting audible announcements.

TalkBack initially returned to the title or another item when leaving Sinhala Advice or History. The Android fix restores focus to the initiating button after the returned window is ready. It cancels the pending restore when the user starts another interaction. The HMD TalkBack focus outline was verified on Monthly history and Monthly advice after Back in both locales. The test profile's font scale, timeout, TalkBack and app locale were restored. The temporary ADB mapping was removed and Android returned to the original user.

Final Android verification ran 140 JVM tests with no failures or skips. V1 debug and unsigned release assembly succeeded. Lint reported no issues. The packaged release manifest excludes READ_SMS.

## Live AI and localization

Live Groq and Gemini calls remain unverified. The authorized environment has no configured `GROQ_API_KEY`, `GEMINI_API_KEY`, approved `finora.llm.groq-model`, approved `finora.llm.gemini-model`, or approved numeric call budget. Existing verified rule fallback and aggregate-only privacy protections are unchanged. No provider calls were made for these checks.

The existing 22-pair History and Advice sheet pinned to `11d80ed` awaits native-speaker approval. The 96- and 64-pair approvals and all 354 existing paired definitions were preserved. This change adds one separate pair for review:

| Resource key | English | Sinhala |
|---|---|---|
| `history_reconstructed` | Reconstructed from available records after month-end. | මාසය අවසන් වූ පසු පවතින වාර්තා මත පදනම්ව යළි ගණනය කර ඇත. |

## Reconciliation

T20 is 5/5, T38 is 4/4, T24 is 4/4 and T30 is 7/7. MD04 is **20/20 tracked technical checks**. T33 remains 5/5 separately, and MD01–MD03 remain 48/48. The T38 checkbox uses the retained 14/14 matrix, the HMD accessibility and focus verification, and the D42 recreation evidence. HMD physical rotation remains unverified. No INDEX status changed. T38 still depends on T09, T33 on T23, and T30 on T25, T29 and T38. Independent review remains open.

NO-GO to start MD05 under the current dependency gates. In INDEX, T31 and T34 require T17, T18 and T19. T32 requires T19. T35 requires T10 and T17–T19, and T36 requires T35. Live-provider verification and translation approval remain separate open MD04 checks.
