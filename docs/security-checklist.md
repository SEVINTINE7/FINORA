# FINORA — T53 Security and Privacy Checklist

Audit date: 2026-11-22
Status: PARTIAL — NOT FINAL SECURITY ACCEPTANCE
Baseline: Local upstream main

## Static inspection results

| Check | Finding | Status |
|---|---|---|
| Password hashing | BCrypt strength 12 in SecurityConfig.java | OBSERVED |
| Registration | PasswordEncoder.encode in AuthService.java | OBSERVED |
| Login | PasswordEncoder.matches in AuthService.java | OBSERVED |
| JWT expiry | Expiration set in JwtService.java | OBSERVED |
| JWT signature | verifyWith(key) in JwtService.java | OBSERVED |
| HTTPS | No enforcement found in searched application sources | UNVERIFIED |
| Error logging | GlobalExceptionHandler logs exception objects | REVIEW REQUIRED |

OBSERVED means the code pattern was found, not that complete security acceptance passed.

## Pending verification

- [ ] JWT lifetime and refresh-token expiration tests
- [ ] Deployment HTTPS/TLS verification
- [ ] Sensitive-data logging review
- [ ] SMS import network privacy (T14)
- [ ] AI request privacy (T27)
- [ ] Comprehensive secret scan
- [ ] Dependency vulnerability scan and triage
- [ ] Final reviewer acceptance

No live SMS or LLM testing, comprehensive secret scan, or dependency vulnerability scan was performed.