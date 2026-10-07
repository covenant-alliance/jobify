# Back-end backlog

Ordered by priority. **B** = bug or security, **P** = product feature, **T** = tech debt. Each item names the front-end impact. Confirm any item still applies before starting it, because this was written from a code read on 2026-09-30 and nothing was run.

## Bugs and security (do first)

**B1. ✅ CORS blocks PATCH.** `SecurityConfig.corsConfigurationSource` allows `GET, POST, PUT, DELETE, OPTIONS` only. The front end calls `PATCH /jobs/{id}/available` (open/close a job), so the browser preflight fails. Add `PATCH`. *Front-end impact: fixes the employer open/close toggle.*

**B2. ✅ Employers see everyone's jobs.** `GET /jobs` has no owner filter, and the employer panel calls it unfiltered. Add `GET /jobs/mine` (EMPLOYER only), or an `employer=me` param, and tell the front-end session to switch.

**B3. ✅ No ownership or role checks on job writes.** `PATCH /jobs/{id}/available` states "any authenticated user can update availability". `POST /jobs` only checks "authenticated", so a SEEKER token gets "Employer not found" (404) rather than a proper 403. Enforce `ROLE_EMPLOYER` for create, and ownership for update, in the service layer. Add tests.

**B4. ✅ Stored XSS through `jobDescription`.** The front end sends HTML from a rich-text editor and DOMPurify only protects one client. Sanitize server-side (for example the OWASP Java HTML Sanitizer, with an allow-list of p, br, ul, ol, li, strong, em, h2, h3, a[href]) on write. Decide and document a max length. `api-documentation.md` says 1000, but the column is `@Lob`.

**B5. ✅ Entity used as request body.** `POST /jobs` binds `@RequestBody JobPost` (a JPA entity, with no `@Valid`), so clients can set `postId`, `employer`, `available` and so on. Introduce `CreateJobRequest` (title, description, rating, rate, location, workMode, employmentType, requiredSkills: string[]) with Bean Validation. Keep the JSON field names exactly as today so the front end doesn't break. A `PUT /jobs/{id}` (edit) is the natural companion.

**B6. ✅ `jobRating` is employer-supplied.** *(Decided by the PO on 2026-09-30: removed from the API, see P11 / #28.)* The posting employer sets the "rating". Product question: is it really an employer rating or quality score? The UI exposes it as an input. Clarify before building on it (the fake match score uses it).

**B7. ✅ JWT secret and lifetime.** *(Done in sprint 1: `JWT_SECRET` env var, startup validation. Refresh tokens remain a follow-up spike.)* Secret is committed in `application.properties`. Move to `${JWT_SECRET}`, and fail startup if it's missing outside the `dev` profile. Consider refresh tokens. The front end just logs out on 401 or expiry.

**B8. ✅ No valid token answered 403 instead of 401 (#50, found by the T1 tests).** Now 401 with the standard envelope (and `WWW-Authenticate: Bearer`); signed-in-but-not-allowed is 403 with a message instead of an empty body. Also fixed in the same change: a valid token for an account that no longer exists (for example after an approved deletion) caused a server error instead of 401.

## Product features the front end is waiting on (P1 is the big one)

**P11. ✅ Job compensation model (#28).** `rate` + `rateType` (`HOURLY | MONTHLY | YEARLY | CONTRACT_TOTAL`) replace `hourlyRate` and `jobRating`; `EmploymentType.B2B` added. Requested by the front end. Done in sprint 2. Follow-ups done: `job_rating` dropped in T3 (migration V2); `hourly_rate` and the `hourlyRate` response field dropped in #55 (migration V4) once the front end stopped reading it.

**P1. Applications.** *(Split into stories #30 apply + list mine, #31 withdraw, #32 employer applicants, #33 employer stage changes. all four are done and merged (PR #40).)* Entity: seeker, job, status (`APPLIED, IN_REVIEW, INTERVIEW, OFFER, REJECTED, WITHDRAWN`), createdAt, updatedAt, optional cover note. Unique on (seeker, job). Endpoints: `POST /jobs/{id}/apply` (SEEKER), `GET /applications/me`, `DELETE /applications/{id}` (withdraw), `GET /jobs/{id}/applications` (owning EMPLOYER), `PUT /applications/{id}/status` (owning EMPLOYER). This replaces the fake stage in `applicationStage.ts`. Also unblocks the company pipeline and funnel widgets and the notification triggers.

**P2. ✅ Saved jobs (#10, done in sprint 2).** `PUT /jobs/{id}/save`, `DELETE /jobs/{id}/save`, `GET /jobs/saved` (SEEKER). Small, and independent of P1.

**P3. Matching engine.** *(PARKED 2026-10-04: the front end is deliberately not touching it; moved to the icebox, issue #12.)* Design in `../job-matching-engine.md`. The data model is ready (`SeekerSkill`, `JobPost.requiredSkills`, location, workMode, employmentType, rate). Nothing is computed. Suggest a `MatchingService` with a pure scoring function, unit-tested heavily, and weights in config. Then `GET /jobs/recommended` and `GET /jobs/{id}/match` (a 0–100 score plus `matchedSkills[]` and `missingSkills[]`), and later `GET /jobs/{id}/candidates`. The front end has a slot on every job card. **Open decision:** how to use `JobPreferences`, which is currently unreachable. Expose `GET/PUT /users/seekers/me/preferences` (salary range, title, location, employment type) so the score has input beyond skills.

**P4. Server-side search and paging.** *(DONE 2026-10-06, issue #13: new route `GET /jobs/search`; see `docs/BACKEND_STATE.md`. Follow-ups: trigram index if text search gets slow; facet counts if wanted.)* Original idea: `GET /jobs?q=&location=&workMode=&employmentType=&minRate=&maxRate=&skills=&available=&page=&size=&sort=`. Today the front end downloads everything and filters in the browser. To avoid breaking callers (`useJobs`, `sitemap.ts`, the employer panel), add paging only when `page` is supplied, or add a new route `/jobs/search`. Coordinate the response envelope.

**P5. ✅ Company name on job responses (#11, done in sprint 2: `companyName`, `companyId`, public `GET /companies/{id}`).** `JobPostResponse` has `employerUsername` only, and the UI title-cases it as the company. Add `companyName` (nullable) and `companyId`. Additive, so safe. Also consider a public `GET /companies/{id}`.

**P6. ✅ Notifications (#14, done in sprint 4; "new match" events wait for the parked matching engine, old read notifications are not cleaned up yet).** `Notification` entity (user, type, title, body, read, createdAt) with `GET /notifications`, `POST /notifications/{id}/read` and `POST /notifications/read-all`. Emit on application status change, new application (employer), and new match (P3). The front-end types are in `app/lib/mockDashboardData.ts` (`type: interview | application | system | hiring | account`).

**P7. ✅ Admin stats and user management (#15, done in sprint 4: paged and searchable `GET /admin/users`, `GET /admin/stats`, audit log). Not built: disable/delete a user directly and job moderation (the optional stretch).** `GET /admin/users` exists and is unpaged. Add paging and search, plus `GET /admin/stats` (users by role, open jobs, applications). Optional: disable or delete user, and job moderation.

**P8. ✅ Employer stats (#16, done in sprint 4: `GET /employers/me/stats`; counts are current-status snapshots, not a stage history; no job views).** *(Open question for the Product Owner on job "views", see #16.)* Per-job application counts, funnel counts by status, and applications over time (`GET /employers/me/stats`) for the company dashboard.

**P9. User preferences.** *(DONE 2026-10-07, issue #25; see `docs/BACKEND_STATE.md` and `docs/NOTIFICATIONS.md`.)* Optional `GET/PUT /users/me/preferences` (notification toggles) to replace the front end's `localStorage` copy. Low priority.

**P12. Company logo (#34).** *(DONE 2026-10-06, see `docs/BACKEND_STATE.md`.)* Upload plus `logoUrl` on jobs and companies. The front end derives initials and a colour until it exists.

**P13. Benefits and gallery on jobs (#35).** *(DONE 2026-10-06, see `docs/BACKEND_STATE.md`.)* `benefits[]` and `images[]`; the front end keeps hardcoded pools meanwhile.

**P14. Responsibilities and requirements (#36).** *(DONE 2026-10-06: Product Owner chose structured fields; see `docs/BACKEND_STATE.md`.)* Keep the front end's sentence-splitting heuristic, or add structured fields.

**P15. Location normalization (#37).** *(DONE 2026-10-06: a `locations` catalogue behind the free text, `GET /jobs/locations`, `locationId` filter; see `docs/BACKEND_STATE.md`. Follow-up if wanted: a country-level facet, and a gazetteer so unknown cities and misspellings can be recognised.)*

**P16. ✅ Seeker application statistics (#38, done in sprint 4).** `GET /applications/me/stats` for the dashboard charts. Depends on P1.

**P17. Require location, workMode, employmentType on new jobs? (#39, decision).** Lets the front end drop its invented fallback values.

**P18. ✅ Application status history and a true funnel (#53, done in sprint 6).** Every status change is recorded (`application_status_changes`, Flyway V3, cascades on delete); `GET /employers/me/stats` gains `applications.funnel` and `perJob[].funnel` (`reached` per stage, cumulative; `medianDaysInStage`), additive. Older applications get a minimal history at startup (stages between and the stage rejected/withdrawn applications left from are unknown). Not done: a history endpoint per application (a timeline for seeker or employer), time-to-hire, per-stage conversion percentages (the front end can compute them from `reached`).

**P10. Company teams (#26, epic).** *(Product Owner decision 2026-10-07: the "employee" is a person who connects to a company and manages its jobs and activity.)* Today one employer account = one company; what is missing is several people per company. Stories: #71 membership model (OWNER/MANAGER) and one access check, #72 invitations / join / leave, #73 company dashboard and team activity feed, #74 notifications for the whole team. Recommended defaults, awaiting confirmation on #26: not a new top-level role (membership roles on the existing EMPLOYER), invite by username, one company per user, MANAGER vs OWNER permissions as in #71. **Built 2026-10-07: #71, #72, #73, #74 (see `docs/BACKEND_STATE.md`); the epic stays open until the front end ships the screens.**

## Tech debt and platform

**T1. ✅ Test foundation (#8, done in sprint 5).** JaCoCo in `mvn test`; a coverage floor enforced by `mvn verify` and CI (lines 90%, branches 80%). Measured 2026-10-06: **95.5% lines, 84.5% branches, 345 tests** (also green on PostgreSQL 18). Added in the last round: `RouteSecurityMatrixTest` (walks every registered route: nothing is public by accident, `/admin/**` refuses non-admins), seeker-profile CRUD for all four sections with cross-user ownership checks, resume upload/download/replace/delete including bad files and path traversal, employer and company rules, CMS admin edits, error-envelope tests, the rate-limit counter, the file store boundary and the copy tool's command line. Known leftovers: `user/model/JobPreferences` and `SalaryRangeValidator` are not reachable from any endpoint, so they stay uncovered (see backlog on the matching engine). Found by the new tests: #50 (no token gives 403, not 401).

**T2. ✅ Bean Validation everywhere (#17, done in sprint 2: all bodies validated, guard test, limits, framework errors no longer 500). Follow-up: "X not found with id: Y" wording of 404s is shown verbatim by the front end and could be friendlier.** Audit every request DTO for `@Valid` and constraints. Check that `MethodArgumentNotValidException` yields a readable 400 `message` in the envelope, since the front end prints it.

**T3. ✅ PostgreSQL 18 + Flyway + Docker (#18, done in sprint 5).** `postgres` profile (env-driven, no default secrets, no demo data, H2 console and Swagger off), Flyway `V1__baseline` (20 tables) and `V2__drop_job_rating`, first admin from `BOOTSTRAP_ADMIN_*`, `Dockerfile` + `docker-compose.yml` (`postgres:18`), CI on H2 and PostgreSQL 18, a database-neutral copy tool (`DatabaseCopyTool`) and the guides `docs/DEPLOYMENT.md` and `docs/DATABASE_MIGRATION.md` (H2 to PostgreSQL, server to server, PostgreSQL to MySQL). Verified against a real PostgreSQL 18.6; the Docker files are not yet built or run (no Docker daemon where they were written). Still open: dropping `hourly_rate` (the API still returns `hourlyRate`; needs the front end to stop reading it), a MySQL profile (guide only), a Redis-backed rate limit if more than one instance is ever run.

**T4. ✅ Dead code removed and legacy `model/` package dissolved (#19, done in sprint 5).** `job/model/Job.java` and `model/Region.java` deleted (a grep showed they only referenced each other, and `Job` was not an entity). `JobPost` and `WorkMode` moved to `job/model`, `Skill` and `ProficiencyLevel` to `user/model`, `EmploymentType` (used by both slices) to `common/model`. Packages only: no table, column, JSON or enum name changed.

**T5. ✅ Observability and audit logging (#20, done in sprint 5).** Correlation id on every request (`X-Request-Id` in and out, with `requestId`, `clientIp`, `user` on every log line), JSON logs on the `postgres` profile (`LOG_FORMAT`), health details for admins only, and one `AUDIT` format for login success/failure/lockout, rate limiting, registration, password changes, deletion requests and approvals, admin actions and access denials. Not done: shipping logs, metrics/tracing (Micrometer/OpenTelemetry), a database-backed audit table, alert rules.

**T6. ✅ API docs (#21, done in sprint 2: all routes documented; OpenAPI JSON checked by a test).** Bring `api-documentation.md` up to date (account, CMS, seeker profile, job fields, admin) or generate it from `/v3/api-docs`. Point the front-end session at the OpenAPI JSON.

**T7. Dependencies.** *(DONE 2026-10-06, issue #22: Spring Boot 4.0.8 GA, springdoc 3.0.3, Dependabot added. Not done: an OWASP dependency-check job; Boot 4.1 is a separate upgrade.)*

**T8. ✅ Cleanup (#23, done in sprint 3: login/register rate limit, failed-login lock, password policy, security event logging).** Also consider a rate limit on `/auth/**` and password rules (front end currently enforces none beyond non-empty).
