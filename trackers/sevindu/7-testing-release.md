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
- [x] T56.1 Ubuntu 22.04 server: firewall (22, 443 only), non-root service user, systemd unit for the API jar
- [x] T56.2 MySQL bound to localhost, app user with least privileges
- [x] T56.3 Nginx + Let's Encrypt HTTPS reverse proxy; secrets in env file readable only by service user
- [x] T56.4 Nightly encrypted `mysqldump` backup, keep per T36.3; do one restore drill
- [ ] T56.5 Signed release APKs (`v1` and `beta`); keystore never in Git
- [x] T56.6 Rollback steps in `docs/deploy.md` — §6 + `deploy/release.sh` (auto-rollback on failed health check) and `deploy/rollback.sh`

**Done when**
- [x] HTTPS health check works from a phone; restore drill done and noted

**Handoff notes:**
- 2026-11-21 Sevindu: all server files in `deploy/` (`server-setup.sh` does T56.1–T56.4 in one run; systemd, Nginx, MySQL bind, certbot hooks, backup/restore/release/rollback scripts). Runbook: `docs/deploy.md`. D21 = backups GPG AES256, kept 30 days (also answers T36.3).
- MySQL users: `finora_app` (DML only), `finora_migrate` (Flyway DDL via `SPRING_FLYWAY_USER`), `finora_backup` (read-only). Port 80 opens only during certbot runs.
- Verified locally: `bash -n` on all scripts; backup of `finora_test` → AES256 (`cipher 9`), wrong passphrase rejected, 21 tables + "Dump completed" after decrypt, 31-day file expired / 29-day kept. Restore drill NOT run (needs a DB it may drop).
- T56.5: signing via `FINORA_KEYSTORE_*` env vars in `android/app/build.gradle`; throwaway keystore → both release APKs pass `apksigner verify`. Real keystore not created yet. Debug build + unit tests unchanged.
- D56 (Sahan relaying Sevindu): domain and stock-Android-trusted HTTPS will be provided during T56. Run the server setup, restore drill and phone HTTPS check. Record T40.1 authenticated V1 Release evidence with Sahan before closing it. Isolated HTTP evidence is development-only.
- 2026-11-25: `FIREBASE_CREDENTIALS` added to `finora.env.example` + generated env (T34). Local backup of synthetic V23 `finora_test`: AES256 file has no plaintext, wrong passphrase rejected, dump complete. No server/domain yet; keystore deferred. Restore drill not run (dropping a DB needs explicit approval).
- 2026-11-27 Sevindu, live server `finorafinance.duckdns.org` (AWS EC2, Ubuntu 24.04 per D59, so T56.1's "22.04" is superseded): ufw active, default deny, only 22/443 allowed (AWS security group further limits 22 to team IPs); `finora-api`, `finora-backup.timer`, `nginx` active; API runs as `finora` and listens on 127.0.0.1:8080; MySQL on 127.0.0.1:3306/33060 only; `/etc/finora/finora.env` is `-r-------- finora`, backup files root-only; Let's Encrypt ECDSA cert valid to 2027-02-24. Public probe: health UP, ports 80/3306/8080 closed.
- Release `api-20261127T070942Z.jar` (main `29e73a9`, D60 PATCH /me) went live via `finora-release` after a pre-release backup. Restore drill of `finora-20261127T070941Z.sql.gz.gpg` -> `finora_restore_drill`: RESTORE DRILL PASSED (34 tables, equal row counts; drill DB dropped). Repeat the drill once real user data exists.
- 2026-11-27: `SHOW GRANTS` for `finora_app`@localhost = USAGE on *.* plus SELECT, INSERT, UPDATE, DELETE on `finora`.* only (T56.2). Phone on mobile data opened `https://finorafinance.duckdns.org/actuator/health` -> `"status":"UP"` with no certificate warning (Done when). Only T56.5 (real keystore, signed `v1`/`beta` APKs) remains.

---

### T57 · End-to-end acceptance and handoff · `ALL`
**Lead:** Sevindu · **Support:** Sahan, Thilina · **Depends on:** all tasks · **Touches:** `docs/acceptance.md`
- [ ] T57.1 On a real phone: register → add records/SMS import → dashboard → patterns → score → advice → daily guide/simulator
- [ ] T57.2 Admin: all 5 functions; Staff: all 4 functions
- [ ] T57.3 `beta` shows two unlabeled advices + ratings; `v1` shows one
- [ ] T57.4 List open defects; document only verified working features

**Done when**
- [ ] `docs/acceptance.md` filled with real results/screenshots

**Handoff notes:** [MD01–MD05 review evidence](../../docs/md05-delivery-handoff.md) is available on draft PR #16. End-to-end acceptance under T57 remains open, including planning integration, deployment verification and owner reconciliation of the historical beta wording with D03.
