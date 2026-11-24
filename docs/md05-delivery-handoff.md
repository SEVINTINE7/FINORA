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
| MD05 | Sahan: T40, T48, T49, T51. Sevindu: T41, T43, T45, T46, T47, T56. Thilina: T52, T53. T43 follows T41; T46 follows T08/T43; T47 follows T46; T48 follows T46/T47; T49 follows T46/T45; T51 follows T38. | T40 awaits acoustic confirmation and authenticated release HTTPS. T41 needs T42/T44 dashboard destinations. T45 needs approved bank-rule authoring and validation fixtures. T47 needs an owner decision on the literal redaction criterion under the canonical-request rejection contract. Thilina retains T52's full role/ownership/privacy matrix and CI and T53's deployment/provider and security evidence. [MD05 evidence](md05-implementation-checkpoint.md) · [Admin and STAFF](../trackers/sevindu/6-admin-staff.md) · [QA](../trackers/thilina/5-qa.md) |
| Other relevant delegated prerequisites | Sevindu: T07 and T36. Sahan: T07.4 and T07.5. | Registration evidence covers both consent gates. T07.5 still needs the T36 data-deletion flow. Consent wording has its own team and native-speaker review. [Consent tracker](../trackers/sevindu/2-auth.md) |

Native-speaker approval is requested for the exact [70 app pairs and four saved replies at `7339893`](md05-sinhala-review.md). Earlier approvals cover only their original 183 pairs. GitHub delivery itself still requires separate authorization.
