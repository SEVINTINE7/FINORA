# FINORA

AI-based personal spending analysis and advice app for young adults in Sri Lanka. ICBT CSE5015, Group 9.

| Folder | What |
|---|---|
| `api/` | Spring Boot 4 REST API (Java 17, Maven, MySQL 8) |
| `android/` | Android app (Java, Android 8.0+) — created in T37 |
| `docs/` | Decisions, DB schema, API contract |
| `trackers/` | Task trackers — start at `trackers/INDEX.md` |

AI tools: read `CLAUDE.md` (Claude) / `AGENTS.md` (Codex, others) first.

## Prerequisites
- JDK 17 (`java -version`)
- MySQL Server 8 running on `localhost:3306`
- Git
- Android Studio (for `android/`)

No separate Maven install needed — use the wrapper (`mvnw` / `mvnw.cmd`).

## 1. Clone
```bash
git clone https://github.com/SEVINTINE7/FINORA.git
cd FINORA
```

## 2. Create the local databases (once per machine)
Creates `finora` (dev) and `finora_test` (tests) plus a **local-only** user `finora` / `finora`.

- **MySQL Workbench:** connect as root → File → Open SQL Script → `api/scripts/create-local-db.sql` → Execute.
- **Command line (Windows):**
  ```powershell
  & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p -e "source api/scripts/create-local-db.sql"
  ```

## 3. Environment variables (optional locally)
Profiles: `dev` (local run), `test` (JUnit), `prod` (server). Defaults below apply in `dev`/`test` only; `prod` has no defaults, so every variable must be set there.

| Variable | Default (dev / test) | Used for |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/finora` | running the API |
| `DB_USER` / `DB_PASS` | `finora` / `finora` | running the API |
| `TEST_DB_URL` | `jdbc:mysql://localhost:3306/finora_test` | tests |
| `TEST_DB_USER` / `TEST_DB_PASS` | `finora` / `finora` | tests |
| `JWT_SECRET` | — (test profile has a test-only value) | signing tokens (T05) |
| `GROQ_API_KEY` / `GEMINI_API_KEY` | — | AI advice (T27) |

Instead of env vars you can copy `api/application-local.yml.example` to `api/application-local.yml` (git-ignored) and put local secrets there. Never commit keys.

## 4. Build, test, run the API
```bash
cd api
./mvnw test                 # Windows: mvnw.cmd test
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # Windows: mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```
Check it is up: <http://localhost:8080/actuator/health>. In `dev` the OpenAPI JSON is at <http://localhost:8080/v3/api-docs>. Endpoint list: [`docs/api-contract.md`](docs/api-contract.md).

## 5. Android app
Open the `android/` folder in Android Studio (available after T37).

## Git workflow
Commit straight to `main` (decision D13): `git pull --rebase` before you start and before you push; commit messages start with the task ID, e.g. `T09.2: categories CRUD`; never force-push.

CI (GitHub Actions) runs the API tests on every push.
