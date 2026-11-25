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
