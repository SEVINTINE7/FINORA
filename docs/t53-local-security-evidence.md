# T53 Local Security Evidence

## Inspected baseline

- Commit: 94e8335bac1098f69fa01bb47cfcb0f99aecef74
- Tree: 58631bd5c1e12793549261152d4b4b2c9e52fc60
- Integrated Maven verify: 438/438 PASS.
- D55 category regressions: 6/6 PASS.
- V19 to V22 isolated database upgrade: PASS.

## Secret scanning

Gitleaks 8.30.0 archive and checksum manifest verified.

- Git history: four generic-api-key findings.
- Tracked source snapshot: two generic-api-key findings.
- All six classified as known synthetic test fixtures.
- Zero unresolved reported findings.
- No scanner rules suppressed.
- No history rewritten.

Locations:

- StaffProvisioningControllerTest.java, line 29.
- DataRightsControllerTest.java, line 57.

Historical revisions inspected:

- e17ffc9daa35aa85872ba383229e80df0b5f1cd1
- d0dee36e011b1817bfa0b73f12383d7e4d2c07ab
- 7946a882a7ec501ad8469c509dcce6dadb22f34e

Execution 18C compared exact reported source lines with
known synthetic fixtures. No secret values are retained
in this evidence document.

Coverage is limited to scanned local Git history and
the exported tracked source snapshot. This is not proof
that all possible credentials are absent.

## Dependency assessment

OWASP Dependency-Check 13.0.0 returned exit 1 twice.
Both attempts failed to obtain vulnerability data due
to TLS certificate validation errors.

The Windows native trust-store recovery also failed.
The temporary JVM override was restored.

No vulnerability report was generated. Dependency
assessment therefore remains BLOCKED, not PASS.

## Outstanding acceptance

- Complete backend and Android dependency assessment.
- Review and remediate applicable vulnerability findings.
- Complete deployed HTTPS and runtime privacy evidence.
- Complete independent security review.
- Preserve final integrated CI evidence.

T53 remains in-progress. No task closure is claimed.

## Alternative vulnerability-feed attempt

Execution 18D stopped during mirror preflight.
OWASP did not execute. No dependency report was generated.
Only RuntimeException was retained; exact cause is unresolved.
The vulnerability assessment remains BLOCKED.
No TLS validation or scanner rules were disabled.

## Execution 33 security and advice verification

Baseline `b48be9b29a445441651e3aa21a627fab401d3894` is the merged PR #17 tree.
It matches the published `15c4ce2` tree. The unpublished T52 commits are excluded.

### Dependency assessment

OWASP Dependency-Check 13.0.0 remains **BLOCKED**. The retained Maven logs distinguish
a Java.net repository metadata TLS warning from the fatal NVD updater error. Both attempts
stopped in `NvdApiDataSource.processApi` with the client reporting an invalid zero-length
API key and a subsequent `NoDataException`. This does not establish the original server's
HTTP status. The retained cache has an H2 file and auxiliary metadata, but no proven
populated and current CVE snapshot. The alternate mirror stopped before a scan. A new
bounded Java 17 probe using standard trust returned HTTP 200 from the NIST CVE 2.0 API
and the official modified-feed metadata endpoint. Endpoint reachability does not validate
a full feed refresh, freshness, or an OWASP report. No failed scanner command was repeated.

The current GitHub Actions workflow on Ubuntu ran API and Android builds for PR #17. It
has no dependency-security scan. Sevindu can approve an isolated CI scan on an exact
revision with a checksum-verified supported Dependency-Check release, an existing authorized
NVD credential or a compatible verified fresh cache/feed, and resolved backend JARs and
Android JAR/AAR/plugin graphs. Preserve severity thresholds and TLS verification. Record
the feed revision or successful update time, enabled analyzers, failures, and report scope.
No enrollment, spending, credential transfer, or CI workflow change occurred here.

A separate **complementary assessment** queried the official OSV querybatch API on
2026-11-25 around 10:30 UTC. The API is unversioned data at query time. Advisory update
timestamps were retained, and a database snapshot age was not exposed. Only public Maven
ecosystem dependency coordinates were sent. The tool was a bounded PowerShell client
of the official API, not an OSV-Scanner CLI or an OWASP analyzer.

| Scope | Exact coordinates queried | Advisory matches |
|---|---:|---:|
| Maven resolved test classpath | 156 | 0 |
| Spring Boot packaged runtime libraries, including launcher tooling | 101 | 0 |
| Android V1 release runtime | 63 | 0 |
| Android V1 debug runtime | 63 | 0 |
| Android V1 debug JVM test runtime | 66 | 0 |
| Android app Gradle build-plugin classpath | 113 | 11 |

The scopes overlap. There were **334 distinct coordinates** including the separately
queried Spring Boot jarmode JAR. The Maven artifact inventory uses the exact Surefire
classpath and the retained tree-equivalent PR #17 packaged JAR. Gradle 9.5.0 resolved
the Android graphs offline on JBR 25. The results cover ecosystem version matches.
They do not establish reachability, runtime exploitability, shaded/native contents,
the Gradle/JDK distributions, emulator instrumentation dependencies, or absence of vulnerabilities.

| Android build-classpath artifact | Resolved version | Advisory and published fix |
|---|---|---|
| `com.google.guava:guava` | 33.4.0-jre | [GHSA-xxph-c9ww-hj94](https://osv.dev/vulnerability/GHSA-xxph-c9ww-hj94), 33.7.2 |
| `org.apache.commons:commons-lang3` | 3.16.0 | [GHSA-j288-q9x7-2f5v](https://osv.dev/vulnerability/GHSA-j288-q9x7-2f5v), 3.18.0 |
| `org.bitbucket.b_c:jose4j` | 0.9.5 | [GHSA-3677-xxcr-wjqv](https://osv.dev/vulnerability/GHSA-3677-xxcr-wjqv), 0.9.6 |
| `org.bouncycastle:bcpkix-jdk18on` | 1.79 | [GHSA-wg6q-6289-32hp](https://osv.dev/vulnerability/GHSA-wg6q-6289-32hp), 1.84 |
| `org.bouncycastle:bcprov-jdk18on` | 1.79 | [GHSA-574f-3g2m-x479](https://osv.dev/vulnerability/GHSA-574f-3g2m-x479), [GHSA-9pwp-9qqc-pr26](https://osv.dev/vulnerability/GHSA-9pwp-9qqc-pr26), [GHSA-c3fc-8qff-9hwx](https://osv.dev/vulnerability/GHSA-c3fc-8qff-9hwx), [GHSA-p93r-85wp-75v3](https://osv.dev/vulnerability/GHSA-p93r-85wp-75v3), [GHSA-qp49-qgx5-5m26](https://osv.dev/vulnerability/GHSA-qp49-qgx5-5m26). Check a compatible plugin resolution with 1.85 for all listed fixes. |
| `org.jdom:jdom2` | 2.0.6 | [GHSA-2363-cqg2-863c](https://osv.dev/vulnerability/GHSA-2363-cqg2-863c), 2.0.6.1 |
| `org.jetbrains.kotlin:kotlin-gradle-plugin` | 2.2.10 | [GHSA-r937-wjx7-w2jp](https://osv.dev/vulnerability/GHSA-r937-wjx7-w2jp), first listed fix 2.4.20-Beta1 |

All eleven matches are version-applicable to the resolved Android build graph. Path-specific
reachability remains unverified. Confirm CI and developer build-cache trust and whether build
tasks consume untrusted inputs before assigning exposure. Sahan and Sevindu should review
a compatible AGP/Kotlin toolchain update and re-resolve both scope graphs before accepting any
override. The audit introduces no dependency changes or suppression.

Sources: [OWASP remote data sources](https://dependency-check.github.io/DependencyCheck/data/index.html),
[NIST NVD feeds](https://nvd.nist.gov/vuln/data-feeds), [OSV querybatch API](https://google.github.io/osv.dev/post-v1-querybatch/).

### Differential secret assessment

Gitleaks 8.30.0 archive matched the retained publisher checksum manifest and the installed binary
matched the verified ZIP entry. The retained default rules and empty ignore file were used.
With full redaction, Git range `94e8335..b48be9b` covered 38 commits and returned zero findings.
The differential source set had 41 paths, including the new tests, and returned zero findings.
Existing synthetic findings remain classified in the earlier evidence. The retrospective range and
working source check do not cover unrelated branches, ignored files, or external secret stores.

### Local runtime and advice evidence

- The Groq and Gemini adapters constructed their normal HTTPS requests. A test-only transport
  copied each original body publisher and synthetic auth header to a controlled loopback server.
  Actual JSON envelopes and embedded fact/action objects were checked. Synthetic private names,
  account/category text, merchants and notes were absent. Success logs contained metadata and
  excluded the synthetic key, captured prompt markers and reply. Production endpoints and redirect
  restrictions were untouched. No external provider was contacted.
- The existing Android repository JVM test captured the structured PASTE batch over loopback.
  It checked exact item and transaction keys and identical bytes on the explicit retry. The
  shared preview source projects parsed fields into the batch DTO. This provides serializer
  evidence. An end-to-end emulator capture from the paste and inbox UI remains open.
- T25 held ledger records fixed while a budget-limit change moved the budget from NEAR to OVER.
  The canonical actions, aggregate facts and exact LKR number provenance stayed equal.
- Provider-disabled T30 V1 HTTP verified complete delivered keys, exact nested number fields,
  decimal 50.23, nullable ID/rating on the empty result, and canonical fallback with private
  markers absent. The Android JVM fixture decoded the matching payload into MonthlyRepository
  DTOs, including nullable values and exact BigDecimal.
- Java 17 focused Maven ran 3 tests with zero failures, errors and skips. The tightened Gemini
  envelope check was rerun alone and passed. Gradle Android JVM ran 2 focused methods with
  zero failures, errors and skips. These are test-only changes. No full backend verify or
  JaCoCo acceptance gate ran on this follow-up, and no T52 baseline suite was repeated.

T53 still needs an accepted OWASP dependency report, build-tool triage, deployed HTTPS
under T56, scoped emulator SMS capture with Sahan, authorized external provider evidence if
required for acceptance, and independent review.

## Execution 46 wrapper integrity and dependency baseline

Base: 812897e21fb999ebc0ff556d538a76ddb6fc818a. The synchronized team copy
was at e53b8e550f7e615581894fd777bbdf99ef79a8b6 and contained D01–D13.
No later candidate-evaluation approval or OWASP data source was recovered there.

- Pinned Maven 3.10.1 SHA-256 after downloading the official ZIP from Maven Central,
  matching its SHA-512 sidecar, and calculating SHA-256 from those verified bytes.
  Windows wrapper bootstrap passed with an empty wrapper cache. A separate disposable
  bootstrap with a deliberately incorrect digest refused the archive.
- Verified the existing Gradle 9.5.0 distribution pin and the checked-in wrapper JAR
  against the [official Gradle release checksums](https://gradle.org/release-checksums/).
  The Android workflow now checks the JAR before invoking it. The exact Bash command
  accepted the current JAR and rejected a wrong digest locally. Hosted CI is pending
  publication of this branch. The dependency cache configuration stays as it was.
- Resolved the current Android build classpath and V1 debug runtime graph with Gradle
  9.5.0, AGP 9.3.0 and Java 17.0.21.1. The build graph still selects BC provider,
  PKIX and utility 1.79, KGP 2.2.10, Guava 33.4.0-jre, Commons Lang 3.16.0,
  jose4j 0.9.5 and JDOM 2.0.6. These modules were absent from the resolved debug
  runtime graph. No dependency versions changed and the previous eleven advisory
  matches remain open. This was a graph reconciliation using the retained OSV inventory.
- Candidate POMs for AGP 9.4.1 and KGP 2.4.21 were available in their official
  repositories. AGP 9.4 needs Gradle 9.6 and Java 17. The Kotlin compatibility
  table lists full KGP 2.4.21 support only through AGP 9.3.1. No candidate was
  configured or built. Avoid treating the two candidates as a verified pair.
- Dependency verification metadata remains open pending trusted artifact provenance.
  OWASP remains blocked on an authorized NVD update credential or a verified current,
  compatible vulnerability database. No update or scan was attempted. The prior OSV
  evidence and the Execution 45 cold-cache HTTP 429 followed by a successful same-revision
  retry retain their original scope.

Baseline verification on Java 17 passed ordinary and acceptance V1 debug assembly,
instrumentation compilation, 150 JVM tests with no failures, errors or skips, and
unsigned release packaging including release vital lint. Full debug lint crashed
inside Android lint with NoSuchMethodError for java.util.List.removeLast() while
the bidirectional-text detector processed BudgetDto.java. No lint checks were disabled.
The baseline release is not minified, so this run does not verify R8.
