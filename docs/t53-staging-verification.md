# T53 staging security verification

Execution 53 verified the harness on local fixtures only. No staging hostname was
recorded or contacted. T56 deployment and operational approval remain with Sevindu.

## Before running against staging

1. Record the approved exact origin, API revision, stock-Android-trusted certificate
   policy, HTTP policy and operator approval. Current T56 closes port 80 except for
   certificate issuance. The runner accepts explicit port refusal. A timeout is
   inconclusive and needs separate firewall evidence; it does not pass this check.
   Enable the redirect option only for an explicitly approved same-host HTTPS redirect.
2. Have the deployment owner verify the effective provider-disabled setting and
   empty provider and Firebase credentials without sharing secret values. Record
   the revision and verdict. The HTTP runner does not query deployment internals or
   call advice, notification or delivery endpoints to discover configuration.
3. Prepare two disposable, consented synthetic USER accounts with distinct active
   synthetic financial accounts through the normal application workflow. Record the two
   owned account IDs. For A, obtain a genuine access token from the approved project
   in a separate session and wait until it expires. Supply only a token from the same
   staging deployment and identity. The runner checks only the fixture's expiry and
   subject locally; successful fresh sign-in and server response supply the API evidence.
4. Set FINORA_STAGE_APPROVED_ORIGIN to the canonical https origin on standard port 443
   with no trailing slash. Set FINORA_STAGE_OWNER_APPROVED and
   FINORA_STAGE_PROVIDERS_AND_FIREBASE_OFF_VERIFIED to yes only after the corresponding
   approvals and checks. Set FINORA_STAGE_SYNTHETIC_FIXTURES_VERIFIED to yes only
   after verifying both synthetic accounts and the genuine expired-token provenance.
   Start the process with TLS key logging disabled. Inject FINORA_STAGE_A_EMAIL, FINORA_STAGE_A_PASSWORD,
   FINORA_STAGE_A_ACCOUNT_ID, the equivalent B variables, and FINORA_STAGE_A_EXPIRED_TOKEN
   into a dedicated process through the approved secret mechanism. Keep them out of
   command-line values, committed files and retained evidence.

Run python -B docs/evaluation-tools/staging_security.py locally. For a separately
approved HTTPS redirect, add --allow-approved-https-redirect. The HTTP check never
carries authorization or a body and redirect responses are inspected without following
them. The HTTPS connections use default trust and hostname verification. The harness
checks health, anonymous and expired rejection, login, both owner lists and account
details, bidirectional cross-owner 404, and fixed field allowlists. Account routes use
only the two supplied fixture IDs. It performs no financial mutation. Login can create
server-side sessions, so arrange normal fixture session lifecycle with the owner.

Retain only the fixed outcome, UTC time, approved endpoint/revision, fixture labels
without IDs, and operator attestations. Do not preserve headers, response or request
bodies, financial values, or session tokens. A local PASS does not establish deployed
HTTPS or T40.1. Separately verify signed V1 Release authentication on stock Android
under D56, and complete T56 health and restore evidence.

## Local verification

Run python -B -m unittest discover -s docs/evaluation-tools -p test_staging_security.py -v.
To include the loopback TLS test, set FINORA_TEST_OPENSSL to a trusted local OpenSSL
binary. It creates a temporary local certificate with the localhost DNS name. The
test client trusts that certificate only within the test; the staging CLI provides
no custom certificate or trust-bypass option. Execution 53 passed 6/6 checks, including
certificate trust and hostname rejection using real loopback TLS. The auth and finance
contract responses and HTTP redirect/refusal behavior used local mocked fixtures.
Execution 54 passed those six checks plus two focused refusal checks for TLS key
logging and missing synthetic-fixture attestation.

## Long-term synthetic data

Keep a separately approved synthetic staging application and schema, with T56's
distinct app, migration and backup roles. Use disposable functional accounts for
long-lived multi-month tests. Reserve two separate controlled benchmark accounts and
keep their benchmark source revision, fixture version, counts, totals and digest.
Do not write imports, task rehearsals or routine functional activity into either
benchmark account. Include backup access, expiry and restore into a separate approved
schema in the owner handoff.

The normal authenticated USER API is the supported staging mechanism for creating
ordinary synthetic functional fixtures. No approved full canonical staging import
mechanism was found. Before staging the canonical T55 dataset, obtain an approved
fixture import or replay plan that reconciles all data types, historical state and
fingerprints. Confirm coverage and isolation with a read-only verification before
accepting benchmark data. Leave the existing persistent loader tied to its dedicated
loopback UUID, schema and owners. Do not point it at staging, relax its guards, or
treat a partial ordinary API replay as the original benchmark.
