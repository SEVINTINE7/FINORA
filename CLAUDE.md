# FINORA — AI Development Guide

Android app + REST API that helps young adults in Sri Lanka (18–30) track spending, see patterns, get a 0–100 health score and checked advice. ICBT CSE5015, Group 9. Team: Sevindu (lead, API/DB/admin), Sahan (Android/UI/SMS), Thilina (analytics/AI/QA).

## Stack
- `/api` — Java 17 · Spring Boot 4.1 (D14) · Maven wrapper · Spring Security + JWT · Spring Data JPA · Flyway · MySQL 8 · JUnit 5 / Mockito
- `/android` — Java · minSdk 26 (Android 8.0) · Gradle wrapper · Retrofit/OkHttp · MPAndroidChart · EncryptedSharedPreferences · FCM · Espresso · the `v1` flavor
- `/docs` — `decisions.md`, `db-schema.md` (from T03), `api-contract.md` (from T04)
- LLM: Groq, Gemini backup — called **only** from `/api`.

## Path shorthands used in trackers
- `api:` = `api/src/main/java/lk/finora/api/` · `api-test:` = `api/src/test/java/lk/finora/api/`
- `mig:` = `api/src/main/resources/db/migration/`
- `and:` = `android/app/src/main/java/lk/finora/app/` · `res:` = `android/app/src/main/res/`

## Commands (exist after T02 / T37; on Windows use `mvnw.cmd` / `gradlew.bat`)
- API test: `cd api && ./mvnw test` · run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- Android test: `cd android && ./gradlew testV1DebugUnitTest` · build: `./gradlew assembleV1Debug`

## Conventions
- Money: `BigDecimal` / `DECIMAL(15,2)`, LKR only. Never `double`/`float`.
- Timestamps UTC `DATETIME(6)`; transaction dates `DATE`.
- Endpoints under `/api/v1`. Controllers take/return DTOs only, never entities. Bean Validation on DTOs.
- Error JSON: `{"timestamp","status","code","message","path"}`; codes like `VALIDATION_ERROR`, `NOT_FOUND`, `CONFLICT`, `FORBIDDEN`.
- Another user's resource → **404** (not 403). Repositories query `findByIdAndUserId`.
- Package by feature: `api:<feature>/` with `Controller`, `Service`, `Repository`, `Entity`, `dto/`.
- Android: ViewModel + LiveData + repository; all visible strings in `res/values/strings.xml` and `res/values-si/strings.xml`.
- Secrets only in env vars or git-ignored `application-local.yml`. Never commit keys.

## Non-negotiable rules
1. Full SMS text never leaves the phone; only user-confirmed amount/date/merchant/type are sent.
2. The LLM gets only whitelisted aggregate numbers and category labels — no names, merchants, emails, account numbers.
3. Every number in AI advice is checked against calculated facts; on failure show rule-based advice.
4. No bank logins. Users see only their own data. Admin/Staff never see user transactions.
5. Never claim tests passed without running them and showing the output.

## How to do a task ("do T09" or "do T09.1–T09.3")
0. `git pull --rebase` first. Everyone commits straight to `main` (D13): message starts with the task ID (`T09.2: categories CRUD`), pull again before pushing, never force-push.
1. Look up the task in `trackers/INDEX.md` (file + status). Open that file and read **only that task's section**, plus the **Handoff notes** of the tasks it depends on.
2. If a dependency is not `done`, stop and say which one.
3. Read only the `docs/decisions.md` entries and the files listed under **Touches** (plus what they import).
4. Do the subtasks in order. "T09.1–T09.3" means only those.
5. Run the checks in **Done when** and show real output.
6. Tick `[x]` only on verified subtasks, update the status in `INDEX.md` (`todo` → `in-progress` → `review` → `done`), and write ≤6 lines of **Handoff notes** (files changed, decisions, leftovers).
7. If a decision is missing, ask the human, then record it in `docs/decisions.md`.
8. Tasks marked **Detail: outline** must first be expanded (exact fields, endpoints, tests) and shown to the human before coding.

`done` = human reviewed + tests passing.

## Android testing and acceptance audits

Before planning or running Android acceptance, follow the [Android acceptance testing guide](docs/android-acceptance-testing.md). It applies to MD05, the final MD01–MD05 audit, and subsequent testing. Complete autonomous preparation first, then group checks that require a person into as few sessions as practical.
