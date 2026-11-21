# Sevindu 7 — API tests, deployment, acceptance (T50, T56, T57)
Rules and workflow: see `CLAUDE.md`. Status lives in `trackers/INDEX.md`.
**Detail: outline** — expand before starting (T50 expanded).

---

### T50 · Backend API test suite · `ALL`
**Lead:** Sevindu · **Support:** Thilina · **Depends on:** T05 (grows with every API task) · **Touches:** `api-test:`, `api/pom.xml`, `.github/workflows/ci.yml`
- [x] T50.1 Every controller has integration tests for success, validation, auth and ownership — per-controller tests cover success/400/ownership-404; `api-test:security/EndpointAuthTest` reads every `/api/v1` endpoint from Spring's mappings and checks no/bad token → 401 `UNAUTHORIZED`, wrong role → 403 `FORBIDDEN` (rule 4), auth endpoints public
- [x] T50.2 Full suite runs against real MySQL 8 (D11) in CI — CI runs `./mvnw -B verify` on the `mysql:8.0` service; uploads `jacoco-report` (always) and `surefire-reports` (on failure). First CI run green (a41041f)
- [x] T50.3 Add JaCoCo coverage report; target ≥70% line coverage on services — `jacoco-maven-plugin` 0.8.14, report at `target/site/jacoco/`; `check-services` fails `verify` below 70% (all `*Service` classes together)


**Done when**
- [x] `./mvnw verify` green in CI; coverage report saved as CI artifact — Sevindu reported successful CI on `a41041f`, with 98.5% service coverage and 96.4% overall coverage locally. Thilina independently verified the combined code on 2026-11-21: 146/146 tests passed, 0 failures/errors/skips, BUILD SUCCESS (exit 0), JaCoCo >=70% service coverage gate passed.

**Handoff notes:**

- Thilina (PR #1, `4ea18dd`): 9 new analytics/security tests; 9/9 targeted, 22/22 analytics, 141/141 full local tests passed with Java 17, Maven 3.10.1 and isolated MySQL 8. Evidence: local Surefire reports and `t50-full-test.log`.
- Sevindu (`a41041f`): `EndpointAuthTest` checks `/api/v1` endpoints for missing/invalid tokens (401), incorrect roles (403) and public authentication routes.
- Authorization: ADMIN for `/api/v1/admin/**`, STAFF for `/api/v1/staff/**`, USER elsewhere; retain cross-user ownership 404 tests for new controllers.
- JaCoCo 0.8.14: service-line coverage gate ≥70% in `api/pom.xml`; Sevindu reported 98.5% service coverage and 96.4% overall coverage. A temporary 99% gate correctly failed.
- Local verification (Thilina, 2026-11-21): Combined Maven verify passed: 146/146 tests, 0 failures/errors/skips, BUILD SUCCESS (exit 0). JaCoCo >=70% service coverage gate passed using Java 17 and isolated MySQL 8 finora_test.
- CI now runs `verify` with MySQL 8 and configured report artifacts. First green CI run and artifact verification remain pending; T50 stays in-progress.


---

### T56 · Deployment and backups · `RELEASE`
**Lead:** Sevindu · **Depends on:** T04 · **Touches:** `deploy/`, `docs/deploy.md`
- [ ] T56.1 Ubuntu 22.04 server: firewall (22, 443 only), non-root service user, systemd unit for the API jar
- [ ] T56.2 MySQL bound to localhost, app user with least privileges
- [ ] T56.3 Nginx + Let's Encrypt HTTPS reverse proxy; secrets in env file readable only by service user
- [ ] T56.4 Nightly encrypted `mysqldump` backup, keep per T36.3; do one restore drill
- [ ] T56.5 Signed release APKs (`v1` and `beta`); keystore never in Git
- [x] T56.6 Rollback steps in `docs/deploy.md` — §6 + `deploy/release.sh` (auto-rollback on failed health check) and `deploy/rollback.sh`

**Done when**
- [ ] HTTPS health check works from a phone; restore drill done and noted

**Handoff notes:**
- 2026-11-21 Sevindu: all server files in `deploy/` (`server-setup.sh` does T56.1–T56.4 in one run; systemd, Nginx, MySQL bind, certbot hooks, backup/restore/release/rollback scripts). Runbook: `docs/deploy.md`. D21 = backups GPG AES256, kept 30 days (also answers T36.3).
- MySQL users: `finora_app` (DML only), `finora_migrate` (Flyway DDL via `SPRING_FLYWAY_USER`), `finora_backup` (read-only). Port 80 opens only during certbot runs.
- Verified locally: `bash -n` on all scripts; backup of `finora_test` → AES256 (`cipher 9`), wrong passphrase rejected, 21 tables + "Dump completed" after decrypt, 31-day file expired / 29-day kept. Restore drill NOT run (needs a DB it may drop).
- T56.5: signing via `FINORA_KEYSTORE_*` env vars in `android/app/build.gradle`; throwaway keystore → both release APKs pass `apksigner verify`. Real keystore not created yet. Debug build + unit tests unchanged.
- Left (needs a server + domain): run `server-setup.sh`, restore drill (fill table in deploy.md), phone HTTPS check, then tick T56.1–T56.5.

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
