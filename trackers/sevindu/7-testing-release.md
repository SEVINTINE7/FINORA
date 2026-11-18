# Sevindu 7 — API tests, deployment, acceptance (T50, T56, T57)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting.

---

### T50 · Backend API test suite · `ALL`
**Lead:** Sevindu · **Support:** Thilina · **Depends on:** T05 (grows with every API task) · **Touches:** `api-test:`
- [ ] T50.1 Every controller has integration tests for success, validation, auth and ownership
- [ ] T50.2 Full suite runs against real MySQL 8 (D11) in CI
- [ ] T50.3 Add JaCoCo coverage report; target ≥70% line coverage on services

**Done when**
- [ ] `./mvnw verify` green in CI; coverage report saved as CI artifact

**Handoff notes:** —

---

### T56 · Deployment and backups · `RELEASE`
**Lead:** Sevindu · **Depends on:** T04 · **Touches:** `deploy/`, `docs/deploy.md`
- [ ] T56.1 Ubuntu 22.04 server: firewall (22, 443 only), non-root service user, systemd unit for the API jar
- [ ] T56.2 MySQL bound to localhost, app user with least privileges
- [ ] T56.3 Nginx + Let's Encrypt HTTPS reverse proxy; secrets in env file readable only by service user
- [ ] T56.4 Nightly encrypted `mysqldump` backup, keep per T36.3; do one restore drill
- [ ] T56.5 Signed release APKs (`v1` and `beta`); keystore never in Git
- [ ] T56.6 Rollback steps in `docs/deploy.md`

**Done when**
- [ ] HTTPS health check works from a phone; restore drill done and noted

**Handoff notes:** —

---

### T57 · End-to-end acceptance and handoff · `ALL`
**Lead:** Sevindu · **Support:** Sahan, Thilina · **Depends on:** all tasks · **Touches:** `docs/acceptance.md`
- [ ] T57.1 On a real phone: register → add records/SMS import → dashboard → patterns → score → advice → daily guide/simulator
- [ ] T57.2 Admin: all 5 functions; Staff: all 4 functions
- [ ] T57.3 `beta` shows two unlabeled advices + ratings; `v1` shows one
- [ ] T57.4 List open defects; document only verified working features

**Done when**
- [ ] `docs/acceptance.md` filled with real results/screenshots

**Handoff notes:** —
