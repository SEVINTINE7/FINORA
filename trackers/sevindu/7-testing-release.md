# Sevindu 7 — API tests, deployment, acceptance (T50, T56, T57)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting (T50 expanded).

---

### T50 · Backend API test suite · `ALL`
**Lead:** Sevindu · **Support:** Thilina · **Depends on:** T05 (grows with every API task) · **Touches:** `api-test:`, `api/pom.xml`, `.github/workflows/ci.yml`
- [x] T50.1 Every controller has integration tests for success, validation, auth and ownership — per-controller tests cover success/400/ownership-404; `api-test:security/EndpointAuthTest` reads every `/api/v1` endpoint from Spring's mappings and checks no/bad token → 401 `UNAUTHORIZED`, wrong role → 403 `FORBIDDEN` (rule 4), auth endpoints public
- [ ] T50.2 Full suite runs against real MySQL 8 (D11) in CI — CI runs `./mvnw -B verify` on the `mysql:8.0` service; uploads `jacoco-report` (always) and `surefire-reports` (on failure). Tick after the first green CI run
- [x] T50.3 Add JaCoCo coverage report; target ≥70% line coverage on services — `jacoco-maven-plugin` 0.8.14, report at `target/site/jacoco/`; `check-services` fails `verify` below 70% (all `*Service` classes together)

**Done when**
- [ ] `./mvnw verify` green in CI; coverage report saved as CI artifact — local `verify` green: 137/137 tests, services 98.5% lines, whole API 96.4%

**Handoff notes:**
- New `api-test:security/EndpointAuthTest` finds endpoints automatically: new controllers (admin/staff/advice) are checked with no extra code. `/api/v1/admin/**` → ADMIN, `/api/v1/staff/**` → STAFF, everything else → USER.
- Ownership 404 tests already existed in every `/{id}` controller test; keep adding a `otherUsers…Gives404` test in each new controller test.
- Coverage gate: `jacoco:check` in `api/pom.xml` (minimum 0.70 on `**/*Service.class`). Checked that it really fails (temporarily set to 0.99 → BUILD FAILURE).
- Local: `mvnw.cmd verify`, then open `api/target/site/jacoco/index.html`.

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
