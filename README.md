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
Local defaults match step 2. Override when needed (always on the server):

| Variable | Default | Used for |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/finora` | running the API |
| `DB_USER` / `DB_PASS` | `finora` / `finora` | running the API |
| `TEST_DB_URL` | `jdbc:mysql://localhost:3306/finora_test` | tests |
| `TEST_DB_USER` / `TEST_DB_PASS` | `finora` / `finora` | tests |

Later tasks add `JWT_SECRET`, `GROQ_API_KEY`, `GEMINI_API_KEY` — never commit them.

## 4. Build, test, run the API
```bash
cd api
./mvnw test                 # Windows: mvnw.cmd test
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```
Check it is up: <http://localhost:8080/actuator/health>

## 5. Android app
Open the `android/` folder in Android Studio (available after T37).

## Git workflow
Commit straight to `main` (decision D13): `git pull --rebase` before you start and before you push; commit messages start with the task ID, e.g. `T09.2: categories CRUD`; never force-push.

CI (GitHub Actions) runs the API tests on every push.
