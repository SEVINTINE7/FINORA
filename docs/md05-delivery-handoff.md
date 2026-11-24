# FINORA delivery handoff through MD05

Tracked technical acceptance is **82/84**. MD01–MD04 retain 68/68 and MD05 retains 14/16. T40.1 and T40's device checklist are the two open MD05 boxes. Other tasks retain their original ownership, formal approvals and prerequisite order.

## One physical listening pass

Use the HMD Crest's existing isolated test profile and the D50 package. The operator stages each screen and moves focus. The listener only confirms the words heard, plus any missing or unintelligible labels. Leave TalkBack on across both languages, then restore the profile, font and accessibility settings.

| Stage | Confirm audible in English and Sinhala |
| --- | --- |
| USER FAQ | Help entry, FAQ title and heading, then Help after Back |
| USER Support | Support entry, ticket status and reply action, then Support after Back |
| STAFF Support | Assigned ticket status, saved-reply choice, preview and confirmation, then Support after Back |
| Privacy feedback | User-facing retry and expired-session wording when staged with synthetic data |

Use retained focus, layout, Recents and session evidence. Android reports accessibility audio activity without its words. Its public playback capture API does not capture accessibility audio usage. Avoid using audio activity, text-to-speech logs or UI node text as proof of audibility. [Android playback capture](https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration)

## One private HTTPS authorization

Sevindu can provide an existing approved, private, Android-trusted staging origin with a synthetic database and an isolated device route. Otherwise authorize all of the following together:

1. One hostname under a team-controlled DNS zone, a DNS-01 TXT record or delegated challenge record for that hostname, and certificate issuance from a CA trusted by stock Android.
2. Private DNS resolution for that hostname from the isolated Android environment and a private route to the local HTTPS reverse proxy. Keep the proxy private and forward to the isolated synthetic API. Keep certificate keys outside Git and provision no public app service.
3. Access to the approved synthetic environment and permission to use an ephemeral release signing key on an isolated emulator. Provide hostname and access instructions separately from secrets.

After authorization, verify hostname and certificate chain with stock Android validation, build V1 Release using `FINORA_RELEASE_API_URL`, record the APK hash and verify authenticated session and privacy behavior with synthetic data. Retain only sanitized results. A self-signed or user-installed CA does not satisfy current release trust, and D51/T56 continue to control the outstanding acceptance. DNS-01 validates domain control without publishing the API. [DNS-01 documentation](https://letsencrypt.org/docs/challenge-types/#dns-01-challenge) · [Android release trust configuration](https://developer.android.com/privacy-and-security/security-config)

## Formal reviews and owner handoff

At each row, an independent human should review the linked evidence and exact implementation revision, record approval or specific defects, and have the owner apply the INDEX transition in dependency order. Technical checkmarks alone are insufficient.

| Track | Owners and prerequisite order | Evidence and remaining action |
| --- | --- | --- |
| Foundation and MD01 | Sevindu: T01 and T09. Sahan: T08, T39. T40/T41/T46 wait on T08. T10/T38/T44 wait on T09. | T01 records decisions and local setup. T08 has physical role-routing and lifecycle evidence. T39 has locale, TalkBack and source review evidence. T09 has physical account/category acceptance. Record exact-revision independent approval, including T08, T09 and T39 already in review. [Android foundation](../trackers/sahan/1-app-skeleton.md) · [Records](../trackers/sevindu/3-records.md) |
| MD02 and MD03 | Sevindu: T10, T12 and T45.3. Sahan: T11, T13, T14, T15, T16. T11/T12/T15 need T10. T14 needs T13 and T45.3. T15/T16 need T14. | Exact-cent CRUD, CSV, SMS permissions, parser and batch evidence are retained. D28–D36 early-start records preserve the formal gates. Independently approve T13 in review, then inspect T14/T15/T16 using their synthetic and physical evidence. [Entry and SMS](../trackers/sahan/2-entry-sms.md) · [SMS quality](../trackers/sahan/3-sms-quality.md) |
| MD04 and analytics | Sevindu: T17–T19, T33. Thilina: T21, T23, T25–T29. Sahan: T20, T24, T30, T38. T09 → T38; T10/T17 → T20; T23/T38 → T24; T23 → T33; T25/T29/T38 → T30. | MD04 is 20/20 and delegated T33 is 5/5. Owner decisions, upstream planning acceptance and independent review remain. T25/T26 precede live T27/T28 validation, and T29 feeds T30. Live provider checks need approved models, credentials and a call budget. [MD04 evidence](md04-authorized-closure.md) · [AI tracker](../trackers/thilina/3-ai-pipeline.md) |
| MD05 | Sahan: T40, T48, T49, T51. Sevindu: T41, T43, T45, T46, T47, T56. Thilina: T52, T53. T43 follows T41; T46 follows T08/T43; T47 follows T46; T48 follows T46/T47; T49 follows T46/T45; T51 follows T38. | T40 awaits audible confirmation and authenticated release HTTPS. T47 has verified bounded redaction under D52 and awaits formal dependency closure and independent review. Thilina retains T52's full role/ownership/privacy matrix and CI and T53's deployment/provider and security evidence. [MD05 evidence](md05-implementation-checkpoint.md) · [Admin and STAFF](../trackers/sevindu/6-admin-staff.md) · [QA](../trackers/thilina/5-qa.md) |
| Other relevant delegated prerequisites | Sevindu: T07, T35 and T36. Sahan: T07.4 and T07.5. | Registration evidence covers both consent gates. Settings now opens the T36 flow. The combined physical consent check, D15 re-consent and consent wording review remain open. [Consent tracker](../trackers/sevindu/2-auth.md) |

The [consolidated native-speaker inventory](md05-sinhala-review.md) includes the 70 app pairs and four saved replies at `a9c54667a0f71b8b646fa8405405cfcd06819e00` and 29 separately pinned dependency-screen pairs. All three groups await approval. Earlier approvals cover only their original 183 pairs.

### Operator sequence for the single listening pass

1. Check the HMD serial, D50 package and existing test profile before installing. Record the starting profile, font scale and enabled accessibility services. Reuse the loopback-only disposable API and the opt-in in-process USER and STAFF fixture, then activate TalkBack once in the test profile.
2. Use package-filtered UIAutomator nodes to find Help, Support and the active controls. Move accessibility focus before each listener observation. Navigate the FAQ first, return via Back, then open a synthetic ticket and its reply list. Run the English and Sinhala pass without toggling TalkBack.
3. Use the disposable assigned STAFF fixture and approved saved-reply catalog. Open Support, focus ticket status, open the saved reply chooser and focus its two language choices and confirmation preview. Cancel the preview when finished. Use Back to return to the originating Support control.
4. Stage Retry with the temporary route removed, restore the route and then focus the recovery view. For the expiry message, expire only the synthetic refresh row before a cold launch. Use the listener to confirm the actual wording in both languages. Sign out, restore the recorded settings, remove only the temporary route and verify the starting Android profile.

### ADMIN implementation handoff

- **T41.3 / T42 / T44, Sevindu.** ADMIN USER search and status changes, bilingual default categories and both dashboard links now have targeted MySQL and Android evidence. Review the status audit and credential revocation, V19 default-name isolation and handling of in-use defaults. The INDEX prerequisites and independent approval remain open.
- **T45.1, T45.2 and T45.5, Sevindu with Sahan.** D35 covers the three pinned NSB definitions and synthetic examples. Android can edit a candidate and test a synthetic sample locally. Discard, immutable publication, activation, rollback and seeded-content parsing retain their evidence. Confirm the approval process for changed definitions and how to bind validation evidence to publication before allowing any modified remote definition. Keep raw SMS off the network.
- **T35 / T36 / T07.5, Sevindu with Sahan.** Review the export data allowlist and file-picker flow, password-confirmed deletion under the D39 lock, anonymous audit and old-token rejection. T35 still needs exhaustive ownership fixtures across exported data families. T36's formal dependency on T35 and independent review remain open. T07 still needs its combined physical acceptance and D15's later re-consent path.

### T07.5 physical acceptance preparation

Use the authorized HMD Crest test profile, D50 package, isolated synthetic API and two disposable USER accounts. Stage this after the bilingual listening pass so consent withdrawal or account deletion cannot interrupt those checks.

1. In both languages, open registration and confirm submission stays disabled with either the age or consent checkbox alone. With both checked, register the first disposable account and verify that the current consent version is returned by GET /api/v1/me.
2. In Settings, switch to Sinhala and verify that the screen changes immediately and that GET /api/v1/me returns the new language without changing the name. Reopen the app and verify the saved locale.
3. Withdraw consent from Settings. Verify that the refresh credential is revoked and that GET /api/v1/me returns a null consent version after a fresh synthetic login. Record the actual post-login behavior separately. The D15 re-consent prompt is still open.
4. On the second disposable account, open Delete my data through Settings. Verify cancel leaves the account intact, an incorrect password leaves the account intact, and explicit confirmation with the correct password returns to sign-in. Verify old credentials are rejected using the isolated API.
5. Retain only sanitized results, restore the test profile settings, and remove only temporary connectivity mappings.

### Draft integration review

A read-only merge simulation found a content conflict with Thilina's PR #13 in the API contract. PR #14 also edits that contract, and PRs #10–#12 edit the QA tracker. Those PRs and #15 simulated cleanly when checked individually. None changes a migration file. Reconcile the PR #13 contract additions with the MD05 sections at integration and retain the owners' QA scope.

T45 publication still accepts only the hash-pinned D35 definitions. Before enabling server persistence and activation of changed mappings, the owner needs to approve a versioned authoring contract and a validation gate bound to the exact candidate digest and approved synthetic fixtures. The approved local editor and probe can be used without sending SMS text.
