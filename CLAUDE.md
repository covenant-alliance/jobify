# Jobify Back-End (Spring Boot)

You are the **back-end session** for Jobify, a job-marketplace platform. The Next.js front end lives in a sibling repo and is worked on by a different Claude session. Your job: make the API the front end needs, without breaking what it already calls.

**Read first, in this order**
1. `docs/FRONTEND_STATE.md` — written by the front-end session: what the front end calls today, what it fakes, and what it expects from the back end ("Requests to the back end"). **Then update the GitHub issues** (see "Two-file hand-off" below).
2. `docs/BACKLOG.md` — prioritized back-end work, including known bugs.
3. `api-documentation.md` — API reference. **Stale**: it predates account, CMS, seeker-profile and the job-post enrichment. Trust the controllers over it, and update it when you change an endpoint.
4. `../Claude.md` — workspace-wide rules (workflow, quality gates). Key points are repeated below.
5. `../job-matching-engine.md` — status of the matching feature (nothing implemented yet).

## Layout
- This directory (`jobify/jobify/`) is the git root and Maven project. Branch `master`, remote `origin`. The parent dir is not a repo.
- Front end: `../frontjobfy/` (separate git repo, branch `master`). Read it freely. **Do not edit it** from this session; put required front-end changes in `docs/BACKEND_STATE.md` under "Open requests for the front end".
- Base package `com.mcverse.jobify`, feature-sliced: `auth/ job/ user/ account/ admin/ cms/ common/ config/`. Inside a slice: `controller service repository dto model`.
- Legacy: `model/` (top level) still holds `JobPost`, `Skill`, `WorkMode`, `EmploymentType`, `ProficiencyLevel`, `Region`. `job/model/Job.java` is dead code. Don't add to `model/`; new entities go in their feature slice.

## Stack (actual, not aspirational)
Spring Boot **4.0.0-M3** (milestone), Java 21, Spring Security with hand-rolled **JWT HS256** (jjwt 0.12.6, `auth/security`), Spring Data JPA, **H2 file DB** (`./data/jobifydb`, `ddl-auto=update`, no migrations), springdoc 2.8.8. Port **9080**. Swagger: `/swagger-ui/index.html`. H2 console: `/h2-console`.
`../Claude.md` mentions PostgreSQL, OAuth 2.0 and Docker. None exist yet. Moving to Postgres plus Flyway and adding a Dockerfile is backlog work, so don't assume it.

## Commands
```
mvn compile                 # build
mvn test                    # unit + MockMvc integration tests; coverage report in target/site/jacoco/index.html
mvn spring-boot:run         # then http://localhost:9080
fuser -k 9080/tcp           # free the port if a stale run is holding it
```
Seeded accounts (all password `password`): `admin`, `alice_s`/`bob_s`/`carol_s` (SEEKER), `techcorp`/`startupxyz`/`financegroup` (EMPLOYER). See `demo-users.md`. Seeding runs in `config/DataInitializer` and `SeedService` only when the DB is empty. `cms/ContentSeeder` runs every boot and only inserts missing keys.
Smoke test: `curl -s -X POST localhost:9080/auth/login -H 'Content-Type: application/json' -d '{"username":"techcorp","password":"password"}'`

## Workflow (from ../Claude.md)
1. Branch first: `feature-<brief-description>`. Never commit straight to `master`.
2. Write tests for all new behaviour (JUnit 5, `@SpringBootTest`/`@WebMvcTest`, spring-security-test). Test profile: `src/test/resources/application.properties` (in-memory H2).
3. `mvn test` must pass before every commit.
4. Detailed commit message (what and why), referencing the ticket. Existing style: `feat: …`, `fix: …`, `chore: …`.
5. Follow SOLID, Google Java style, and constructor injection for new code. Older classes use field `@Autowired`; don't mass-rewrite them unless asked.
6. The API contract is shared with the front end. Any change to a request or response shape, status code, or route means updating `api-documentation.md` and adding an entry to `docs/BACKEND_STATE.md` in the same commit.

## Two-file hand-off with the front-end session (do this after every change)
The sessions share no memory, so they talk through two files. Each file has exactly one writer.
- `docs/FRONTEND_STATE.md` is **written by the front-end session** and read by you. Never edit it, except to fix broken formatting.
- `docs/BACKEND_STATE.md` is **written by you** and read by the front-end session.

**Before you start any work**
1. Read `docs/FRONTEND_STATE.md`, especially "Requests to the back end".
2. Update the GitHub issues to match: open an issue for every request that has none, add a comment or label change where a request changes an existing issue's scope, priority or acceptance criteria, and note the issue number back in `docs/BACKEND_STATE.md`.

**After every change (same commit as the code)**
1. Add an entry at the top of the change log in `docs/BACKEND_STATE.md`: what changed, whether the old request/response shape still works, new or changed routes, status codes and error messages, enum values, behaviour worth knowing, and the issue number.
2. Add or tick items under "Open requests for the front end" for anything the front end must now do.
3. Update `api-documentation.md`, and move the issue's Kanban label (`status: in progress` / `in review` / done).
4. Do not consider a change finished until these are done.

**Issue numbers: never write one from memory.** Read the number from the issue itself (`list_issues` or `issue_read`, and check the title matches) before using it in a commit, a pull request, a doc or a label change. A `Closes #N` in a PR body closes that issue on merge, so a wrong number silently closes unrelated work. After a merge, verify the issues that were closed are the ones you meant.

## Conventions you must keep (the front end depends on them)
- **Error envelope**: `{ "success": false, "message": "...", "data": null, "status": <int> }` from `common/exception/GlobalExceptionHandler`. The front end shows `message` verbatim. Write user-readable messages. 401 makes it log the user out. 404 = `ResourceNotFoundException`, 422 = `BusinessRuleException`, 403 = `LicenseValidationException`, 400 = validation.
- **Success responses are bare DTOs**, not wrapped in `ApiResponse`. Keep it that way.
- **Enums serialize as uppercase strings** (`SEEKER`, `FULL_TIME`, `ONSITE`…). The front-end TypeScript unions mirror them exactly, so adding a value means telling the front-end session.
- **Timestamps** are ISO-8601 `LocalDateTime` without a zone.
- **Identity**: `/me` endpoints read the username from the JWT. Profile/company IDs are UUID strings. Job `postId` is an integer.
- **CORS** is configured in `SecurityConfig` from `cors.allowed-origins`. Allowed origins are `localhost:3000` and `:3001`.
- Public routes: `GET /jobs`, `GET /jobs/{id}`, `GET /content`, `/auth/**`, `/actuator/health`, swagger, h2. `/admin/**` needs `ROLE_ADMIN`. Everything else needs a token.

## Security requirements
Validate all input with Bean Validation (`@Valid`). Never expose JPA entities in API responses or accept them as request bodies (`POST /jobs` currently violates this, see the backlog). Enforce **ownership and role** in the service layer, not just "authenticated". Log security events. The JWT secret comes from `JWT_SECRET`. Local runs use the `dev` profile (active by default), which falls back to a public placeholder key. Any other profile fails at startup without `JWT_SECRET`, and also refuses the placeholder.

## Hazards
- `data/` holds the dev H2 DB and uploaded resumes, is git-ignored, and contains the only copy of dev data. Don't delete it casually. With `ddl-auto=update`, entity changes mutate the schema in place.
- Spring Boot 4.0.0-M3 is a milestone. Check compatibility before upgrading any dependency.
- Stray untracked files `Class diagram with UML notation.pdf` and `Link to jobify-backend-review.md` are not yours. Leave them.
